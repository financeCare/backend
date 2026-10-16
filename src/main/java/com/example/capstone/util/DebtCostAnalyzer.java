package com.example.capstone.util;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.engine.calculator.PenaltyCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DebtCostAnalyzer {

    public BigDecimal calculateMonthlyInterest(DebtSim d) {
        return d.getPrincipal()
                .multiply(d.getAnnualInterestRate())
                .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal costIfPay(DebtSim d) {
        return calculateMonthlyInterest(d);
    }

    public BigDecimal costIfSkip(DebtSim d) {
        BigDecimal interest = calculateMonthlyInterest(d);

        BigDecimal penalty = d.getPenaltyAnnualRate() != null 
                ? PenaltyCalculator.calculateMonthly(d.getPrincipal(), d.getPenaltyAnnualRate())
                : BigDecimal.ZERO;

        return interest.add(penalty);
    }

    public BigDecimal effectiveCost(DebtSim d) {
        return costIfSkip(d);
    }
}