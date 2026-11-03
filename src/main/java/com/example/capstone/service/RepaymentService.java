package com.example.capstone.service;

import com.example.capstone.dto.DebtPaymentRequestDTO;
import com.example.capstone.engine.calculator.PenaltyCalculator;
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
        private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Bangkok");

        private final UserService userService;
        private final DebtRepository debtRepository;
        private final DebtTransactionRepository debtTransactionRepository;

        private void createTxn(
                        Debt debt,
                        DebtTxnType type,
                        BigDecimal amount,
                        LocalDate txnDate,
                        UUID referenceId,
                        Long slipId) {

                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                }

                DebtTransaction txn = DebtTransaction.builder()
                                .debt(debt)
                                .txnType(type)
                                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                                .txnDate(txnDate)
                                .referenceId(referenceId)
                                .slipId(slipId)
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

                BigDecimal currentRemaining = remaining;
                for (DebtTransaction chargeTxn : outstandingCharges) {
                        if (currentRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                                break;
                        }
                        currentRemaining = processSingleChargePayment(debt, currentRemaining, paymentDate, paymentType, chargeTxn);
                }

                return remaining.subtract(currentRemaining);
        }

        private BigDecimal processSingleChargePayment(
                        Debt debt,
                        BigDecimal currentRemaining,
                        LocalDate paymentDate,
                        DebtTxnType paymentType,
                        DebtTransaction chargeTxn) {
                BigDecimal chargeRemaining = chargeTxn.getAmount();
                if (chargeRemaining == null || chargeRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                        return currentRemaining;
                }

                BigDecimal payAmount = currentRemaining.min(chargeRemaining);
                chargeTxn.setAmount(chargeRemaining.subtract(payAmount));
                debtTransactionRepository.save(chargeTxn);

                if (paymentType == DebtTxnType.INTEREST_PAYMENT || paymentType == DebtTxnType.PENALTY_INTEREST_PAYMENT) {
                        BigDecimal currentTotal = debt.getTotalInterestPaid() != null ? debt.getTotalInterestPaid() : BigDecimal.ZERO;
                        debt.setTotalInterestPaid(currentTotal.add(payAmount));
                        debtRepository.save(debt);
                }

                createTxn(debt, paymentType, payAmount, paymentDate, chargeTxn.getTransactionId(), null);
                return currentRemaining.subtract(payAmount);
        }

        @Transactional
        public void payDebt(String token, DebtPaymentRequestDTO req) {
                UUID userId = userService.extractUserIdFromToken(token);
                Debt debt = debtRepository.findByDebtIdAndUserId(req.getDebtId(), userId)
                                .orElseThrow(() -> new BusinessException("Debt not found or not owned by user",
                                                HttpStatus.NOT_FOUND));

                processDebtPayment(debt, req.getPaymentAmount(), req.getPaymentDate(), req.getSlipId());
        }

        @Transactional
        public void processDebtPayment(Debt debt, BigDecimal amount, LocalDate paymentDate, Long slipId) {
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException("Payment amount must be greater than zero",
                                        HttpStatus.BAD_REQUEST);
                }

                LocalDate date = (paymentDate != null) ? paymentDate : LocalDate.now(DEFAULT_ZONE);

                createTxn(debt, DebtTxnType.PAYMENT, amount, date, null, slipId);

                BigDecimal remaining = amount;

                // 1. Fee
                BigDecimal feePaid = allocateChargePayment(debt, remaining, date,
                                DebtTxnType.LATE_FEE_CHARGE, DebtTxnType.LATE_FEE_PAYMENT);
                remaining = remaining.subtract(feePaid);

                // 2. Penalty
                BigDecimal penaltyPaid = allocateChargePayment(debt, remaining, date,
                                DebtTxnType.PENALTY_INTEREST_CHARGE, DebtTxnType.PENALTY_INTEREST_PAYMENT);
                remaining = remaining.subtract(penaltyPaid);

                // 3. Interest
                BigDecimal interestPaid = allocateChargePayment(debt, remaining, date,
                                DebtTxnType.INTEREST_CHARGE, DebtTxnType.INTEREST_PAYMENT);
                remaining = remaining.subtract(interestPaid);

                // 4. Principal
                BigDecimal currentPrincipal = debt.getPrincipalOutstanding() != null
                                ? debt.getPrincipalOutstanding()
                                : debt.getPrincipalAmount();

                if (currentPrincipal == null) {
                        currentPrincipal = BigDecimal.ZERO;
                }

                if (remaining.compareTo(BigDecimal.ZERO) > 0 && currentPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal principalPaid = remaining.min(currentPrincipal);
                        debt.setPrincipalOutstanding(currentPrincipal.subtract(principalPaid));

                        createTxn(debt, DebtTxnType.PRINCIPAL_PAYMENT, principalPaid, date, null, slipId);
                        remaining = remaining.subtract(principalPaid);
                }

                // 5. Overpayment
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        createTxn(debt, DebtTxnType.OVERPAYMENT, remaining, date, null, slipId);
                }

                debtRepository.save(debt);
        }

        @Scheduled(cron = "0 0 1 1 * ?")
        @Transactional
        public void runMonthlyAccrualJob() {
                LocalDate today = LocalDate.now(DEFAULT_ZONE);
                List<Debt> activeDebts = debtRepository.findByActiveTrue();

                for (Debt debt : activeDebts) {
                        try {
                                accrueMonthlyCharges(debt, today);
                        } catch (Exception e) {
                                // Ignore or log individual failure so job continues
                        }
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

                BigDecimal monthlyRate = calculateMonthlyRate(debt, rate);
                BigDecimal interest = principal
                                .multiply(monthlyRate)
                                .setScale(2, RoundingMode.HALF_UP);

                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                        createTxn(debt, DebtTxnType.INTEREST_CHARGE, interest, processDate, null, null);
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

                if (!penaltyAlreadyAccrued && 
                    processDate.isAfter(startMonth) && 
                    debt.getPrincipalOutstanding().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal penaltyRate = debt.getPenaltyAnnualRate();
                        if (penaltyRate != null && penaltyRate.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal lateFee = PenaltyCalculator.calculateMonthly(
                                                debt.getPrincipalOutstanding(),
                                                penaltyRate);

                                if (lateFee.compareTo(BigDecimal.ZERO) > 0) {
                                        createTxn(debt, DebtTxnType.LATE_FEE_CHARGE, lateFee, processDate, null, null);
                                }
                        }
                }
        }

        private BigDecimal calculateMonthlyRate(Debt debt, BigDecimal rate) {
                InterestInterval interval = debt.getInterestInterval() != null ? debt.getInterestInterval() : InterestInterval.YEARLY;
                return switch (interval) {
                        case DAILY -> rate.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                        case WEEKLY -> rate.multiply(BigDecimal.valueOf(4)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                        case MONTHLY -> rate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                        case YEARLY -> rate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
                };
        }
}