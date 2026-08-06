package com.example.capstone.util;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.engineImp.calculator.PenaltyCalculator;

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
        // Marginal cost of keeping this debt for one more month
        BigDecimal interest = calculateMonthlyInterest(d);
        
        // Potential penalty if we miss the payment (risk-based cost)
        BigDecimal penalty = d.getPenaltyAnnualRate() != null 
            ? PenaltyCalculator.calculateMonthly(d.getPrincipal(), d.getPenaltyAnnualRate())
            : BigDecimal.ZERO;

        // The "Cost" of this debt is the interest it accrues. 
        // For non-AI optimization, prioritizing by interest rate (Avalanche) is mathematically optimal for minimum interest.
        // However, we return Interest + Penalty to prioritize debts that are expensive AND have penalties.
        return interest.add(penalty);
    }
}