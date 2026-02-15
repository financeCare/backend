package com.example.capstone.engineImp;

import com.example.capstone.dto.DebtSim;
import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.engineInterface.DebtMonthResult;
import com.example.capstone.entity.Debt;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RevolvingEngine implements DebtMonthEngine {

    @Override
    public DebtMonthResult runMonth(DebtSim d, BigDecimal minPaid, BigDecimal extraPaid) {
        DebtMonthResult r = new DebtMonthResult();

        BigDecimal principal = BigDecimal.valueOf(d.getPrincipalAmount());

        BigDecimal annualRate = BigDecimal.valueOf(d.getInterestRate());
        if (annualRate.compareTo(BigDecimal.ONE) > 0) {
            annualRate = annualRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        }
        BigDecimal rateMonthly = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        BigDecimal interest = principal.multiply(rateMonthly).setScale(2, RoundingMode.HALF_UP);
        principal = principal.add(interest);
        r.interestAdded = interest;

        BigDecimal minApplied = minPaid.max(BigDecimal.ZERO).min(principal);
        principal = principal.subtract(minApplied);
        r.minPaidApplied = minApplied;

        BigDecimal extraApplied = extraPaid.max(BigDecimal.ZERO).min(principal);
        principal = principal.subtract(extraApplied);
        r.extraPaidApplied = extraApplied;

        d.setPrincipalAmount(principal.doubleValue());
        return r;
    }
}
