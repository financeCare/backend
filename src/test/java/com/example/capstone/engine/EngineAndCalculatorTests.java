package com.example.capstone.engine;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.domain.PaymentAllocationResult;
import com.example.capstone.engine.calculator.InterestCalculator;
import com.example.capstone.engine.calculator.PenaltyCalculator;
import com.example.capstone.engine.repaymenttype.BulletEngine;
import com.example.capstone.engine.repaymenttype.EMIEngine;
import com.example.capstone.engine.repaymenttype.LumpSumEngine;
import com.example.capstone.engine.repaymenttype.RevolvingEngine;
import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.enums.RepaymentTypeEnum;
import com.example.capstone.util.DebtCostAnalyzer;
import com.example.capstone.util.PenaltyLogic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EngineAndCalculatorTests {

    private DebtSim debt;

    @BeforeEach
    void setUp() {
        debt = new DebtSim();
        debt.setDebtId(UUID.randomUUID());
        debt.setDebtName("Test Debt");
        debt.setPrincipal(new BigDecimal("10000.00"));
        debt.setOriginalPrincipal(new BigDecimal("10000.00"));
        debt.setAnnualInterestRate(new BigDecimal("0.12"));
        debt.setMinPayment(new BigDecimal("1000.00"));
        debt.setRepaymentType(RepaymentTypeEnum.EMI);
        debt.setInterestType(InterestCalculationType.THIRTY_360);
        debt.setStartDate(LocalDate.of(2026, 1, 1));
        debt.setEndDate(LocalDate.of(2027, 1, 1));
        debt.setCurrentDate(LocalDate.of(2026, 2, 10));
        debt.setDueDay(5);
        debt.setGracePeriodDays(3);
        debt.setPenaltyTriggerDays(7);
        debt.setPenaltyAnnualRate(new BigDecimal("0.05"));
        debt.setInterestOutstanding(BigDecimal.ZERO);
        debt.setLateFeeOutstanding(BigDecimal.ZERO);
        debt.setPenaltyOutstanding(BigDecimal.ZERO);
    }

    @Test
    void testInterestCalculatorAllTypes() {
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal rate = new BigDecimal("0.12");

        BigDecimal thirty360 = InterestCalculator.calculate(principal, rate, InterestCalculationType.THIRTY_360);
        assertNotNull(thirty360);
        assertTrue(thirty360.compareTo(BigDecimal.ZERO) > 0);

        BigDecimal dailySimple = InterestCalculator.calculate(principal, rate, InterestCalculationType.DAILY_SIMPLE);
        assertNotNull(dailySimple);
        assertTrue(dailySimple.compareTo(BigDecimal.ZERO) > 0);

        BigDecimal flatRate = InterestCalculator.calculate(principal, rate, InterestCalculationType.FLAT_RATE);
        assertNotNull(flatRate);
        assertTrue(flatRate.compareTo(BigDecimal.ZERO) > 0);

        BigDecimal compound = InterestCalculator.calculate(principal, rate, InterestCalculationType.DAILY_COMPOUND);
        assertNotNull(compound);
        assertTrue(compound.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testPenaltyCalculator() {
        BigDecimal principal = new BigDecimal("5000.00");
        BigDecimal penaltyRate = new BigDecimal("0.05");

        BigDecimal monthly = PenaltyCalculator.calculateMonthly(principal, penaltyRate);
        assertNotNull(monthly);

        BigDecimal daily = PenaltyCalculator.calculateDaily(principal, penaltyRate, 10);
        assertNotNull(daily);
    }

    @Test
    void testPenaltyLogic() {
        // null dates
        DebtSim nullDateDebt = new DebtSim();
        PenaltyLogic.LateResult resNull = PenaltyLogic.checkAndCalculate(nullDateDebt, BigDecimal.ZERO);
        assertFalse(resNull.isLate());

        // Full planned payment -> not late
        PenaltyLogic.LateResult resPaid = PenaltyLogic.checkAndCalculate(debt, new BigDecimal("1000.00"));
        assertFalse(resPaid.isLate());
        assertEquals(BigDecimal.ZERO, resPaid.getPenalty());

        // Underpaid with late days within grace period
        debt.setCurrentDate(LocalDate.of(2026, 2, 6)); // 1 day late <= grace period 3
        PenaltyLogic.LateResult resGrace = PenaltyLogic.checkAndCalculate(debt, new BigDecimal("500.00"));
        assertTrue(resGrace.isLate());
        assertEquals(BigDecimal.ZERO, resGrace.getPenalty());

        // Underpaid with overdueDays > penaltyTriggerDays
        debt.setCurrentDate(LocalDate.of(2026, 2, 20)); // 15 days late > 7 days
        PenaltyLogic.LateResult resLate = PenaltyLogic.checkAndCalculate(debt, new BigDecimal("200.00"));
        assertTrue(resLate.isLate());
        assertTrue(resLate.getPenalty().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testPenaltyEngine() {
        PenaltyEngine engine = new PenaltyEngine();
        BigDecimal pZero = engine.calculatePenalty(debt, new BigDecimal("1000"), new BigDecimal("1200"));
        assertEquals(BigDecimal.ZERO, pZero);

        BigDecimal pMissing = engine.calculatePenalty(debt, new BigDecimal("1000"), new BigDecimal("500"));
        assertTrue(pMissing.compareTo(BigDecimal.ZERO) > 0);

        BigDecimal pMissingDefaultRate = engine.calculatePenalty(null, new BigDecimal("1000"), new BigDecimal("500"));
        assertTrue(pMissingDefaultRate.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testPaymentAllocationEngine() {
        PaymentAllocationEngine engine = new PaymentAllocationEngine();
        PaymentAllocationResult result = engine.allocate(
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("1000.00")
        );

        assertEquals(new BigDecimal("50.00"), result.getPaidToPenalty());
        assertEquals(new BigDecimal("100.00"), result.getPaidToInterest());
        assertEquals(new BigDecimal("350.00"), result.getPaidToPrincipal());
    }

    @Test
    void testDebtCostAnalyzer() {
        DebtCostAnalyzer analyzer = new DebtCostAnalyzer();
        BigDecimal monthlyInt = analyzer.calculateMonthlyInterest(debt);
        assertTrue(monthlyInt.compareTo(BigDecimal.ZERO) > 0);

        BigDecimal payCost = analyzer.costIfPay(debt);
        assertEquals(monthlyInt, payCost);

        BigDecimal skipCost = analyzer.costIfSkip(debt);
        assertTrue(skipCost.compareTo(monthlyInt) >= 0);

        BigDecimal effective = analyzer.effectiveCost(debt);
        assertEquals(skipCost, effective);
    }

    @Test
    void testDefaultLoanMonthEngine() {
        DefaultLoanMonthEngine engine = new DefaultLoanMonthEngine();
        LoanMonthResult res = engine.simulateMonth(debt, new BigDecimal("200.00"));
        assertNotNull(res);
        assertTrue(res.getPrincipalEnd().compareTo(res.getPrincipalStart()) < 0);
    }

    @Test
    void testEMIEngine() {
        EMIEngine engine = new EMIEngine();
        assertTrue(engine.supports("EMI"));
        assertFalse(engine.supports("OTHER"));

        debt.setPenaltyOutstanding(new BigDecimal("50.00"));
        debt.setLateFeeOutstanding(new BigDecimal("20.00"));
        debt.setInterestOutstanding(new BigDecimal("30.00"));

        LoanMonthResult res = engine.runMonth(debt, new BigDecimal("1000.00"), new BigDecimal("500.00"));
        assertNotNull(res);
        assertEquals(0, debt.getPenaltyOutstanding().compareTo(BigDecimal.ZERO));
        assertEquals(0, debt.getLateFeeOutstanding().compareTo(BigDecimal.ZERO));
        assertEquals(0, debt.getInterestOutstanding().compareTo(BigDecimal.ZERO));
    }

    @Test
    void testBulletEngine() {
        BulletEngine engine = new BulletEngine();
        assertTrue(engine.supports("BULLET"));
        assertFalse(engine.supports("OTHER"));

        // Before maturity
        debt.setEndDate(LocalDate.of(2030, 1, 1));
        LoanMonthResult resNotMaturity = engine.runMonth(debt, new BigDecimal("100.00"), new BigDecimal("200.00"));
        assertNotNull(resNotMaturity);

        // At maturity
        debt.setCurrentDate(LocalDate.of(2030, 1, 1));
        LoanMonthResult resMaturity = engine.runMonth(debt, new BigDecimal("15000.00"), BigDecimal.ZERO);
        assertNotNull(resMaturity);
        assertEquals(0, resMaturity.getPrincipalEnd().compareTo(BigDecimal.ZERO));

        // Without end date
        debt.setEndDate(null);
        debt.setCurrentDate(debt.getStartDate().plusMonths(125));
        LoanMonthResult resNoEndMaturity = engine.runMonth(debt, new BigDecimal("15000.00"), BigDecimal.ZERO);
        assertNotNull(resNoEndMaturity);
    }

    @Test
    void testLumpSumEngine() {
        LumpSumEngine engine = new LumpSumEngine();
        assertTrue(engine.supports("LUMPSUM"));
        assertFalse(engine.supports("OTHER"));

        debt.setPenaltyOutstanding(new BigDecimal("10.00"));
        debt.setLateFeeOutstanding(new BigDecimal("10.00"));
        debt.setInterestOutstanding(new BigDecimal("10.00"));

        LoanMonthResult res = engine.runMonth(debt, new BigDecimal("500.00"), new BigDecimal("100.00"));
        assertNotNull(res);
    }

    @Test
    void testRevolvingEngine() {
        RevolvingEngine engine = new RevolvingEngine();
        assertTrue(engine.supports("REVOLVING"));
        assertFalse(engine.supports("OTHER"));

        debt.setPenaltyOutstanding(new BigDecimal("10.00"));
        debt.setLateFeeOutstanding(new BigDecimal("10.00"));
        debt.setInterestOutstanding(new BigDecimal("10.00"));

        LoanMonthResult res = engine.runMonth(debt, new BigDecimal("1200.00"), new BigDecimal("300.00"));
        assertNotNull(res);
    }
}
