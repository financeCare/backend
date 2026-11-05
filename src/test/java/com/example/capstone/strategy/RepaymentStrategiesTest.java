package com.example.capstone.strategy;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.strategy.repayment.AvalancheStrategyInterface;
import com.example.capstone.strategy.repayment.MinimumOnlyStrategyInterface;
import com.example.capstone.strategy.repayment.OptimalCostStrategy;
import com.example.capstone.strategy.repayment.RepaymentStrategyInterface;
import com.example.capstone.strategy.repayment.SnowballStrategyInterface;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The strategies pick the debt that receives the extra money each month. Shared rules: inactive debts are
 * ignored, informal (loan-shark style) debts always win over formal ones, an empty/inactive list gives null.
 */
class RepaymentStrategiesTest {

    private final RepaymentStrategyInterface snowball = new SnowballStrategyInterface();
    private final RepaymentStrategyInterface avalanche = new AvalancheStrategyInterface();
    private final RepaymentStrategyInterface minimumOnly = new MinimumOnlyStrategyInterface();
    private final RepaymentStrategyInterface optimal = new OptimalCostStrategy();

    private DebtSim debt(String name, String principal, String annualRate) {
        DebtSim d = new DebtSim();
        d.setDebtId(UUID.randomUUID());
        d.setDebtName(name);
        d.setPrincipal(new BigDecimal(principal));
        d.setAnnualInterestRate(new BigDecimal(annualRate));
        d.setActive(true);
        d.setInformal(false);
        return d;
    }

    private DebtSim withPenalty(DebtSim d, String penaltyAnnualRate) {
        d.setPenaltyAnnualRate(new BigDecimal(penaltyAnnualRate));
        return d;
    }

    private DebtSim inactive(DebtSim d) {
        d.setActive(false);
        return d;
    }

    private DebtSim informal(DebtSim d) {
        d.setInformal(true);
        return d;
    }

    // ---------- Snowball: smallest balance first ----------

    @Test
    void snowball_picksTheSmallestPrincipal() {
        DebtSim big = debt("big", "100000", "0.05");
        DebtSim small = debt("small", "5000", "0.30");
        DebtSim mid = debt("mid", "20000", "0.10");

        assertSame(small, snowball.apply(List.of(big, small, mid)));
    }

    @Test
    void snowball_ignoresInactiveDebts() {
        DebtSim paidOff = inactive(debt("paid off", "1", "0.01"));
        DebtSim open = debt("open", "5000", "0.10");

        assertSame(open, snowball.apply(List.of(paidOff, open)));
    }

    @Test
    void snowball_prefersInformalDebtEvenWhenAFormalOneIsSmaller() {
        DebtSim formalSmall = debt("bank", "1000", "0.05");
        DebtSim informalBig = informal(debt("loan shark", "50000", "0.60"));

        assertSame(informalBig, snowball.apply(List.of(formalSmall, informalBig)));
    }

    @Test
    void snowball_amongSeveralInformalDebtsPicksTheSmallest() {
        DebtSim a = informal(debt("a", "30000", "0.60"));
        DebtSim b = informal(debt("b", "8000", "0.60"));

        assertSame(b, snowball.apply(List.of(a, b)));
    }

    @Test
    void snowball_tieKeepsTheFirstDebtInTheList() {
        DebtSim first = debt("first", "5000", "0.10");
        DebtSim second = debt("second", "5000", "0.20");

        assertSame(first, snowball.apply(List.of(first, second)));
    }

    // ---------- Avalanche: highest interest rate first ----------

    @Test
    void avalanche_picksTheHighestRate() {
        DebtSim low = debt("low", "1000", "0.05");
        DebtSim high = debt("high", "90000", "0.25");
        DebtSim mid = debt("mid", "500", "0.12");

        assertSame(high, avalanche.apply(List.of(low, high, mid)));
    }

    @Test
    void avalanche_ignoresInactiveDebts() {
        DebtSim closedExpensive = inactive(debt("closed", "1000", "0.99"));
        DebtSim open = debt("open", "1000", "0.10");

        assertSame(open, avalanche.apply(List.of(closedExpensive, open)));
    }

    @Test
    void avalanche_prefersInformalDebtEvenWhenAFormalRateIsHigher() {
        DebtSim formalHigh = debt("card", "10000", "0.28");
        DebtSim informalLow = informal(debt("friend", "10000", "0.02"));

        assertSame(informalLow, avalanche.apply(List.of(formalHigh, informalLow)));
    }

    // ---------- Minimum only: no extra target ----------

    @Test
    void minimumOnly_neverTargetsADebt() {
        assertNull(minimumOnly.apply(List.of(debt("a", "1000", "0.10"), debt("b", "2000", "0.20"))));
        assertNull(minimumOnly.apply(List.of()));
    }

    // ---------- Optimal cost: highest monthly interest + penalty ----------

    @Test
    void optimal_comparesMonthlyCostNotJustTheRate() {
        // 1,000,000 at 10% costs more per month than 10,000 at 30%
        DebtSim largeLowRate = debt("mortgage", "1000000", "0.10");
        DebtSim smallHighRate = debt("card", "10000", "0.30");

        assertSame(largeLowRate, optimal.apply(List.of(smallHighRate, largeLowRate)));
    }

    @Test
    void optimal_countsTheLatePenaltyInTheCost() {
        DebtSim noPenalty = debt("plain", "100000", "0.10");
        DebtSim withPenalty = withPenalty(debt("penalized", "100000", "0.10"), "0.30");

        assertSame(withPenalty, optimal.apply(List.of(noPenalty, withPenalty)));
    }

    @Test
    void optimal_prefersInformalDebt() {
        DebtSim formalCostly = debt("mortgage", "1000000", "0.10");
        DebtSim informalCheap = informal(debt("friend", "1000", "0.05"));

        assertSame(informalCheap, optimal.apply(List.of(formalCostly, informalCheap)));
    }

    // ---------- shared edge cases ----------

    @Test
    void emptyOrAllInactive_givesNoTargetForEveryStrategy() {
        List<DebtSim> none = List.of();
        List<DebtSim> allClosed = List.of(inactive(debt("a", "1000", "0.10")), inactive(debt("b", "2000", "0.20")));

        for (RepaymentStrategyInterface strategy : List.of(snowball, avalanche, optimal)) {
            assertNull(strategy.apply(none));
            assertNull(strategy.apply(allClosed));
        }
    }

    @Test
    void singleActiveDebt_isAlwaysTheTarget() {
        DebtSim only = debt("only", "1234", "0.15");

        for (RepaymentStrategyInterface strategy : List.of(snowball, avalanche, optimal)) {
            assertSame(only, strategy.apply(List.of(only)));
        }
    }
}
