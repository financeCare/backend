package com.example.capstone.service;

import com.example.capstone.dto.DebtPaymentRequestDTO;
import com.example.capstone.engineImp.calculator.PenaltyCalculator;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
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

                        debtTransactionRepository.save(chargeTxn);

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

                Debt debt = debtRepository.findByDebtIdAndUserIdAndActiveTrue(userId, debtPaymentRequestDTO.getDebtId())
                                .orElseThrow(() -> new BusinessException("Debt not found", HttpStatus.NOT_FOUND));

                BigDecimal remaining = debtPaymentRequestDTO.getPaymentAmount();

                createTxn(debt, DebtTxnType.PAYMENT, debtPaymentRequestDTO.getPaymentAmount(),
                                debtPaymentRequestDTO.getPaymentDate(), null);

                remaining = allocateChargePayment(
                                debt,
                                remaining,
                                debtPaymentRequestDTO.getPaymentDate(),
                                DebtTxnType.LATE_FEE_CHARGE,
                                DebtTxnType.LATE_FEE_PAYMENT);

                remaining = allocateChargePayment(
                                debt,
                                remaining,
                                debtPaymentRequestDTO.getPaymentDate(),
                                DebtTxnType.PENALTY_INTEREST_CHARGE,
                                DebtTxnType.PENALTY_INTEREST_PAYMENT);

                remaining = allocateChargePayment(
                                debt,
                                remaining,
                                debtPaymentRequestDTO.getPaymentDate(),
                                DebtTxnType.OVERPAYMENT_FEE_CHARGE,
                                DebtTxnType.OVERPAYMENT_FEE_PAYMENT);

                remaining = allocateChargePayment(
                                debt,
                                remaining,
                                debtPaymentRequestDTO.getPaymentDate(),
                                DebtTxnType.INTEREST_CHARGE,
                                DebtTxnType.INTEREST_PAYMENT);

                if (remaining.compareTo(BigDecimal.ZERO) > 0) {

                        BigDecimal principalOutstanding = debt.getPrincipalOutstanding();

                        if (principalOutstanding.compareTo(BigDecimal.ZERO) > 0) {

                                BigDecimal payToPrincipal = remaining.min(principalOutstanding);

                                debt.setPrincipalOutstanding(
                                                principalOutstanding.subtract(payToPrincipal));

                                debtRepository.save(debt);

                                createTxn(debt,
                                                DebtTxnType.PRINCIPAL_PAYMENT,
                                                payToPrincipal,
                                                debtPaymentRequestDTO.getPaymentDate(),
                                                null);

                                remaining = remaining.subtract(payToPrincipal);
                        }
                }

                if (remaining.compareTo(BigDecimal.ZERO) > 0) {

                        createTxn(debt,
                                        DebtTxnType.OVERPAYMENT,
                                        remaining,
                                        debtPaymentRequestDTO.getPaymentDate(),
                                        null);
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

                boolean alreadyAccrued = debtTransactionRepository.existsByDebtAndTxnTypeAndYearAndMonth(
                                debt,
                                DebtTxnType.INTEREST_CHARGE,
                                year,
                                month);

                if (alreadyAccrued) {
                        return;
                }

                BigDecimal principal = debt.getPrincipalOutstanding();
                BigDecimal annualRate = debt.getInterestRate();

                if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                }

                BigDecimal monthlyRate = annualRate.divide(
                                BigDecimal.valueOf(12),
                                10,
                                RoundingMode.HALF_UP);

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
                int lastMonthYear = lastMonth.getYear();
                int lastMonthValue = lastMonth.getMonthValue();

                boolean penaltyAlreadyAccrued = debtTransactionRepository.existsByDebtAndTxnTypeAndYearAndMonth(
                                debt,
                                DebtTxnType.LATE_FEE_CHARGE,
                                lastMonthYear,
                                lastMonthValue);

                if (!penaltyAlreadyAccrued && debt.getPrincipalOutstanding().compareTo(BigDecimal.ZERO) > 0) {
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