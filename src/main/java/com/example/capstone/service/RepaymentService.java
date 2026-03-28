package com.example.capstone.service;

import com.example.capstone.dto.DebtPaymentRequestDTO;
import com.example.capstone.engineImp.calculator.PenaltyCalculator;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RepaymentService {
        private final UserService userService;
        private final DebtRepository debtRepository;
        private final DebtTransactionRepository debtTransactionRepository;

        private void createTxn(
                        Debt debt,
                        DebtTxnType type,
                        BigDecimal amount,
                        LocalDate txnDate,
                        UUID referenceId) {

                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                }

                DebtTransaction txn = DebtTransaction.builder()
                                .debt(debt)
                                .txnType(type)
                                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                                .txnDate(txnDate)
                                .referenceId(referenceId)
                                .build();

                txn.validate();
                debtTransactionRepository.save(txn);
        }

        private BigDecimal allocateChargePayment(
                        Debt debt,
                        BigDecimal remaining,
                        LocalDate paymentDate,
                        DebtTxnType chargeType,
                        DebtTxnType paymentType) {

                if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                }

                List<DebtTransaction> outstandingCharges = debtTransactionRepository.findOutstandingByType(
                                debt.getDebtId(),
                                chargeType);

                for (DebtTransaction chargeTxn : outstandingCharges) {

                        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                                break;
                        }

                        BigDecimal chargeRemaining = chargeTxn.getAmount();

                        if (chargeRemaining == null ||
                                        chargeRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                                continue;
                        }

                        BigDecimal payAmount = remaining.min(chargeRemaining);

                        chargeTxn.setAmount(chargeRemaining.subtract(payAmount));
                        debtTransactionRepository.save(chargeTxn);

                        // สะสมยอดดอกเบี้ยที่จ่ายไปแล้ว
                        if (paymentType == DebtTxnType.INTEREST_PAYMENT || 
                            paymentType == DebtTxnType.PENALTY_INTEREST_PAYMENT) {
                            BigDecimal currentTotal = debt.getTotalInterestPaid() != null ? 
                                debt.getTotalInterestPaid() : BigDecimal.ZERO;
                            debt.setTotalInterestPaid(currentTotal.add(payAmount));
                            debtRepository.save(debt);
                        }

                        createTxn(
                                        debt,
                                        paymentType,
                                        payAmount,
                                        paymentDate,
                                        chargeTxn.getTransactionId());

                        remaining = remaining.subtract(payAmount);
                }

                return remaining;
        }

        @Transactional
        public void payDebt(String token, DebtPaymentRequestDTO debtPaymentRequestDTO) {
                UUID userId = userService.extractUserIdFromToken(token);
                if (debtPaymentRequestDTO.getPaymentAmount() == null
                                || debtPaymentRequestDTO.getPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException("Payment amount must be > 0", HttpStatus.BAD_REQUEST);
                }

                Debt debt = debtRepository.findByDebtIdAndUserIdAndActiveTrue(debtPaymentRequestDTO.getDebtId(), userId)
                                .orElseThrow(() -> new BusinessException("Debt not found", HttpStatus.NOT_FOUND));

                BigDecimal remaining = debtPaymentRequestDTO.getPaymentAmount();
                LocalDate paymentDate = debtPaymentRequestDTO.getPaymentDate();

                // 0. บันทึกยอดจ่ายรวมในระบบ
                createTxn(debt, DebtTxnType.PAYMENT, remaining, paymentDate, null);

                // Phase 1: Clear All Outstanding Charges currently in the database
                // (This handles historical charges and initialRemaining balances)
                remaining = allocateChargePayment(debt, remaining, paymentDate,
                                DebtTxnType.LATE_FEE_CHARGE, DebtTxnType.LATE_FEE_PAYMENT);
                remaining = allocateChargePayment(debt, remaining, paymentDate,
                                DebtTxnType.PENALTY_INTEREST_CHARGE, DebtTxnType.PENALTY_INTEREST_PAYMENT);
                remaining = allocateChargePayment(debt, remaining, paymentDate,
                                DebtTxnType.OVERPAYMENT_FEE_CHARGE, DebtTxnType.OVERPAYMENT_FEE_PAYMENT);
                remaining = allocateChargePayment(debt, remaining, paymentDate,
                                DebtTxnType.INTEREST_CHARGE, DebtTxnType.INTEREST_PAYMENT);

                // Phase 2: Accrue and Pay Interest for the current month (if not already accrued)
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        accrueMonthlyCharges(debt, paymentDate);
                        remaining = allocateChargePayment(debt, remaining, paymentDate,
                                        DebtTxnType.LATE_FEE_CHARGE, DebtTxnType.LATE_FEE_PAYMENT);
                        remaining = allocateChargePayment(debt, remaining, paymentDate,
                                        DebtTxnType.INTEREST_CHARGE, DebtTxnType.INTEREST_PAYMENT);
                }

                // Phase 3: Apply remaining balance to Principal
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal principalOutstanding = debt.getPrincipalOutstanding();
                        if (principalOutstanding != null && principalOutstanding.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal payToPrincipal = remaining.min(principalOutstanding);
                                
                                debt.setPrincipalOutstanding(principalOutstanding.subtract(payToPrincipal));
                                debtRepository.save(debt);

                                createTxn(debt, DebtTxnType.PRINCIPAL_PAYMENT, payToPrincipal,
                                                paymentDate, null);
                                remaining = remaining.subtract(payToPrincipal);
                        }
                }


                // หากมีเงินเหลือหลังจากหักทุกเดือนแล้ว ให้ลงเป็น Overpayment
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        createTxn(debt, DebtTxnType.OVERPAYMENT, remaining, paymentDate, null);
                }
        }

        @Scheduled(cron = "0 0 1 1 * ?")
        public void monthlyAccrualJob() {
                List<Debt> activeDebts = debtRepository.findByActiveTrue();

                for (Debt debt : activeDebts) {
                        accrueMonthlyCharges(debt, LocalDate.now());
                }
        }

        @Transactional
        public void accrueMonthlyCharges(Debt debt, LocalDate processDate) {

                if (debt.getPrincipalOutstanding() == null ||
                                debt.getPrincipalOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                }

                int year = processDate.getYear();
                int month = processDate.getMonthValue();

                boolean alreadyAccrued = debtTransactionRepository.existsByDebtIdAndTxnTypeAndYearAndMonth(
                                debt.getDebtId(),
                                DebtTxnType.INTEREST_CHARGE,
                                year,
                                month);

                if (alreadyAccrued) {
                        return;
                }

                BigDecimal principal = debt.getPrincipalOutstanding();
                BigDecimal rate = debt.getInterestRate();

                if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                }

                InterestInterval interval = debt.getInterestInterval() != null ? 
                        debt.getInterestInterval() : InterestInterval.YEARLY;

                BigDecimal monthlyRate;
                switch (interval) {
                        case DAILY:
                                // คิดเป็นดอกเบี้ยต่อเดือน (คูณ 30 วันโดยประมาณ)
                                monthlyRate = rate.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                                break;
                        case MONTHLY:
                                // เรทที่กรอกมาคือต่อเดือนอยู่แล้ว (เช่น ร้อยละ 20 ต่อเดือน)
                                monthlyRate = rate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                                break;
                        case YEARLY:
                        default:
                                monthlyRate = rate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
                                break;
                }

                BigDecimal interest = principal
                                .multiply(monthlyRate)
                                .setScale(2, RoundingMode.HALF_UP);

                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                        createTxn(
                                        debt,
                                        DebtTxnType.INTEREST_CHARGE,
                                        interest,
                                        processDate,
                                        null);
                }

                LocalDate lastMonth = processDate.minusMonths(1);
                int lastMonthValue = lastMonth.getMonthValue();
                int lastMonthYear = lastMonth.getYear();

                LocalDate startDate = debt.getStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate startMonth = startDate.withDayOfMonth(1);

                boolean penaltyAlreadyAccrued = debtTransactionRepository.existsByDebtIdAndTxnTypeAndYearAndMonth(
                                debt.getDebtId(),
                                DebtTxnType.LATE_FEE_CHARGE,
                                lastMonthYear,
                                lastMonthValue);

                // Charge penalty only if:
                // 1. Not already accrued for last month
                // 2. Debt existed before this month (cannot be late before it started)
                // 3. Outstanding principal > 0
                if (!penaltyAlreadyAccrued && 
                    processDate.isAfter(startMonth) && 
                    debt.getPrincipalOutstanding().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal penaltyRate = debt.getPenaltyAnnualRate();
                        if (penaltyRate != null && penaltyRate.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal lateFee = PenaltyCalculator.calculateMonthly(
                                                debt.getPrincipalOutstanding(),
                                                penaltyRate);

                                if (lateFee.compareTo(BigDecimal.ZERO) > 0) {
                                        createTxn(
                                                        debt,
                                                        DebtTxnType.LATE_FEE_CHARGE,
                                                        lateFee,
                                                        processDate,
                                                        null);
                                }
                        }
                }
        }
}