package com.example.capstone.service;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How a payment is split (fees, penalty, interest, principal, overpayment) and how interest accrues. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepaymentServiceBehaviourTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);

    @Mock
    private UserService userService;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private DebtTransactionRepository debtTransactionRepository;
    @Mock
    private PlanCacheEvictor planCacheEvictor;

    @InjectMocks
    private RepaymentService service;

    private Debt debt;

    @BeforeEach
    void setUp() {
        debt = new Debt();
        debt.setDebtId(UUID.randomUUID());
        debt.setUserId(UUID.randomUUID());
        debt.setPrincipalOutstanding(new BigDecimal("5000"));
        debt.setInterestRate(new BigDecimal("12"));
        debt.setStartDate(Date.from(LocalDate.of(2026, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        when(debtTransactionRepository.save(any(DebtTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private DebtTransaction charge(DebtTxnType type, String amount) {
        return DebtTransaction.builder().transactionId(UUID.randomUUID()).debt(debt).txnType(type)
                .amount(new BigDecimal(amount)).txnDate(DATE.minusDays(10)).build();
    }

    private void outstanding(DebtTxnType type, DebtTransaction... charges) {
        when(debtTransactionRepository.findOutstandingByType(debt.getDebtId(), type)).thenReturn(List.of(charges));
    }

    private List<DebtTransaction> saved() {
        ArgumentCaptor<DebtTransaction> captor = ArgumentCaptor.forClass(DebtTransaction.class);
        verify(debtTransactionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private BigDecimal amountOf(List<DebtTransaction> txns, DebtTxnType type) {
        return txns.stream().filter(t -> t.getTxnType() == type).map(DebtTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- splitting a payment ----------

    @Test
    void payment_coversFeesThenPenaltyThenInterestThenPrincipal() {
        DebtTransaction fee = charge(DebtTxnType.LATE_FEE_CHARGE, "100");
        DebtTransaction penalty = charge(DebtTxnType.PENALTY_INTEREST_CHARGE, "50");
        DebtTransaction interest = charge(DebtTxnType.INTEREST_CHARGE, "200");
        outstanding(DebtTxnType.LATE_FEE_CHARGE, fee);
        outstanding(DebtTxnType.PENALTY_INTEREST_CHARGE, penalty);
        outstanding(DebtTxnType.INTEREST_CHARGE, interest);

        service.processDebtPayment(debt, new BigDecimal("1000"), DATE, 77L);

        List<DebtTransaction> txns = saved();
        assertEquals(0, new BigDecimal("1000").compareTo(amountOf(txns, DebtTxnType.PAYMENT)));
        assertEquals(0, new BigDecimal("100").compareTo(amountOf(txns, DebtTxnType.LATE_FEE_PAYMENT)));
        assertEquals(0, new BigDecimal("50").compareTo(amountOf(txns, DebtTxnType.PENALTY_INTEREST_PAYMENT)));
        assertEquals(0, new BigDecimal("200").compareTo(amountOf(txns, DebtTxnType.INTEREST_PAYMENT)));
        assertEquals(0, new BigDecimal("650").compareTo(amountOf(txns, DebtTxnType.PRINCIPAL_PAYMENT)));
        assertEquals(0, BigDecimal.ZERO.compareTo(amountOf(txns, DebtTxnType.OVERPAYMENT)));
        assertEquals(0, new BigDecimal("4350").compareTo(debt.getPrincipalOutstanding()));
        // the charges that were paid are used up
        assertEquals(0, BigDecimal.ZERO.compareTo(fee.getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(interest.getAmount()));
        // interest and penalty interest count towards the interest paid
        assertEquals(0, new BigDecimal("250").compareTo(debt.getTotalInterestPaid()));
        verify(debtRepository, org.mockito.Mockito.atLeastOnce()).save(debt);
    }

    @Test
    void payment_smallerThanTheFee_paysPartOfTheFeeAndNothingElse() {
        DebtTransaction fee = charge(DebtTxnType.LATE_FEE_CHARGE, "100");
        outstanding(DebtTxnType.LATE_FEE_CHARGE, fee);

        service.processDebtPayment(debt, new BigDecimal("30"), DATE, null);

        List<DebtTransaction> txns = saved();
        assertEquals(0, new BigDecimal("30").compareTo(amountOf(txns, DebtTxnType.LATE_FEE_PAYMENT)));
        assertEquals(0, BigDecimal.ZERO.compareTo(amountOf(txns, DebtTxnType.PRINCIPAL_PAYMENT)));
        assertEquals(0, new BigDecimal("70").compareTo(fee.getAmount()));                    // 100 - 30 still owed
        assertEquals(0, new BigDecimal("5000").compareTo(debt.getPrincipalOutstanding()));  // principal untouched
    }

    @Test
    void payment_largerThanEverythingOwed_keepsTheRestAsOverpayment() {
        debt.setPrincipalOutstanding(new BigDecimal("1000"));

        service.processDebtPayment(debt, new BigDecimal("10000"), DATE, null);

        List<DebtTransaction> txns = saved();
        assertEquals(0, new BigDecimal("1000").compareTo(amountOf(txns, DebtTxnType.PRINCIPAL_PAYMENT)));
        assertEquals(0, new BigDecimal("9000").compareTo(amountOf(txns, DebtTxnType.OVERPAYMENT)));
        assertEquals(0, BigDecimal.ZERO.compareTo(debt.getPrincipalOutstanding()));
    }

    @Test
    void payment_usesThePrincipalAmountWhenNoOutstandingIsRecorded_andAZeroBalanceMeansOverpayment() {
        debt.setPrincipalOutstanding(null);
        debt.setPrincipalAmount(new BigDecimal("400"));
        service.processDebtPayment(debt, new BigDecimal("500"), DATE, null);
        assertEquals(0, new BigDecimal("400").compareTo(amountOf(saved(), DebtTxnType.PRINCIPAL_PAYMENT)));
        org.mockito.Mockito.clearInvocations(debtTransactionRepository);

        Debt noBalance = new Debt();
        noBalance.setDebtId(UUID.randomUUID());
        service.processDebtPayment(noBalance, new BigDecimal("50"), DATE, null);
        assertEquals(0, new BigDecimal("50").compareTo(amountOf(saved(), DebtTxnType.OVERPAYMENT)));
    }

    @Test
    void payment_ofZeroOrLess_isRejectedWithoutWritingAnything() {
        assertThrows(BusinessException.class, () -> service.processDebtPayment(debt, BigDecimal.ZERO, DATE, null));
        assertThrows(BusinessException.class, () -> service.processDebtPayment(debt, new BigDecimal("-5"), DATE, null));
        assertThrows(BusinessException.class, () -> service.processDebtPayment(debt, null, DATE, null));

        verify(debtTransactionRepository, never()).save(any());
    }

    @Test
    void payment_withoutADate_isRecordedForToday() {
        service.processDebtPayment(debt, new BigDecimal("100"), null, null);

        assertNotNull(saved().get(0).getTxnDate());
    }

    @Test
    void payment_evictsTheCachesOfTheDebtsOwner() {
        service.processDebtPayment(debt, new BigDecimal("100"), DATE, null);

        verify(planCacheEvictor).evictUserCache(debt.getUserId());
    }

    // ---------- monthly accrual ----------

    private BigDecimal accrued(DebtTxnType type) {
        ArgumentCaptor<DebtTransaction> captor = ArgumentCaptor.forClass(DebtTransaction.class);
        verify(debtTransactionRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return amountOf(captor.getAllValues(), type);
    }

    @Test
    void accrual_yearlyRate_chargesOneTwelfthOfThePercentage() {
        service.accrueMonthlyCharges(debt, DATE);

        assertEquals(0, new BigDecimal("50.00").compareTo(accrued(DebtTxnType.INTEREST_CHARGE)));   // 5000 * 12 / 1200
    }

    @Test
    void accrual_otherIntervals_useTheirOwnConversion() {
        debt.setInterestRate(new BigDecimal("1"));

        debt.setInterestInterval(InterestInterval.MONTHLY);
        service.accrueMonthlyCharges(debt, DATE);
        assertEquals(0, new BigDecimal("50.00").compareTo(accrued(DebtTxnType.INTEREST_CHARGE)));   // 1% a month

        org.mockito.Mockito.clearInvocations(debtTransactionRepository);
        debt.setInterestInterval(InterestInterval.WEEKLY);
        service.accrueMonthlyCharges(debt, DATE);
        assertEquals(0, new BigDecimal("200.00").compareTo(accrued(DebtTxnType.INTEREST_CHARGE)));  // 4 x 1%

        org.mockito.Mockito.clearInvocations(debtTransactionRepository);
        debt.setInterestInterval(InterestInterval.DAILY);
        service.accrueMonthlyCharges(debt, DATE);
        assertEquals(0, new BigDecimal("1500.00").compareTo(accrued(DebtTxnType.INTEREST_CHARGE))); // 30 x 1%
    }

    @Test
    void accrual_isSkippedWhenThereIsNothingToChargeOrItWasAlreadyDone() {
        Debt paidOff = new Debt();
        paidOff.setDebtId(UUID.randomUUID());
        paidOff.setPrincipalOutstanding(BigDecimal.ZERO);
        service.accrueMonthlyCharges(paidOff, DATE);

        Debt noPrincipal = new Debt();
        noPrincipal.setDebtId(UUID.randomUUID());
        service.accrueMonthlyCharges(noPrincipal, DATE);

        debt.setInterestRate(BigDecimal.ZERO);
        service.accrueMonthlyCharges(debt, DATE);

        debt.setInterestRate(new BigDecimal("12"));
        when(debtTransactionRepository.existsByDebtIdAndTxnTypeAndYearAndMonth(debt.getDebtId(), DebtTxnType.INTEREST_CHARGE, 2026, 11))
                .thenReturn(true);
        service.accrueMonthlyCharges(debt, DATE);

        verify(debtTransactionRepository, never()).save(any());
    }

    @Test
    void accrual_addsALateFeeWhenAPenaltyRateIsSet_afterTheFirstMonth() {
        debt.setPenaltyAnnualRate(new BigDecimal("24"));

        service.accrueMonthlyCharges(debt, DATE);

        // 5000 * 24 / 1200 = 100
        assertEquals(0, new BigDecimal("100.00").compareTo(accrued(DebtTxnType.LATE_FEE_CHARGE)));
    }

    @Test
    void accrual_noLateFeeInTheMonthTheDebtStarted_orWhenLastMonthAlreadyHasOne() {
        debt.setPenaltyAnnualRate(new BigDecimal("24"));
        // the job runs on the 1st; a debt that started this month has no late fee yet
        LocalDate firstOfMonth = DATE.withDayOfMonth(1);
        debt.setStartDate(Date.from(firstOfMonth.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        service.accrueMonthlyCharges(debt, firstOfMonth);
        assertEquals(0, BigDecimal.ZERO.compareTo(accrued(DebtTxnType.LATE_FEE_CHARGE)));

        org.mockito.Mockito.clearInvocations(debtTransactionRepository);
        debt.setStartDate(Date.from(LocalDate.of(2026, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        when(debtTransactionRepository.existsByDebtIdAndTxnTypeAndYearAndMonth(debt.getDebtId(), DebtTxnType.LATE_FEE_CHARGE, 2026, 10))
                .thenReturn(true);
        service.accrueMonthlyCharges(debt, DATE);
        assertEquals(0, BigDecimal.ZERO.compareTo(accrued(DebtTxnType.LATE_FEE_CHARGE)));
    }

    @Test
    void monthlyJob_continuesWhenOneDebtFails() {
        Debt broken = new Debt();
        broken.setDebtId(UUID.randomUUID());
        broken.setPrincipalOutstanding(new BigDecimal("1000"));
        broken.setInterestRate(new BigDecimal("12"));
        broken.setPenaltyAnnualRate(new BigDecimal("12"));
        broken.setStartDate(null);                               // fails after the interest was charged
        when(debtRepository.findByActiveTrue()).thenReturn(List.of(broken, debt));

        service.runMonthlyAccrualJob();

        // the healthy debt was still processed
        ArgumentCaptor<DebtTransaction> captor = ArgumentCaptor.forClass(DebtTransaction.class);
        verify(debtTransactionRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(t -> t.getDebt() == debt && t.getTxnType() == DebtTxnType.INTEREST_CHARGE));
    }

    @Test
    void payDebt_ofAnotherUsersDebt_isNotFound_andRecordsNothing() {
        UUID debtId = UUID.randomUUID();
        com.example.capstone.dto.DebtPaymentRequestDTO request = new com.example.capstone.dto.DebtPaymentRequestDTO();
        request.setDebtId(debtId);
        request.setPaymentAmount(BigDecimal.TEN);
        UUID userId = UUID.randomUUID();
        when(userService.extractUserIdFromToken("t")).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserId(eq(debtId), eq(userId))).thenReturn(java.util.Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.payDebt("t", request));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
        verify(debtTransactionRepository, never()).save(any());
    }
}
