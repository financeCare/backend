package com.example.capstone.engineImp;

import com.example.capstone.dto.DebtSim;
import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.engineInterface.DebtMonthResult;
import com.example.capstone.entity.Debt;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EmiEngine implements DebtMonthEngine {

    @Override
    public DebtMonthResult runMonth(DebtSim d, BigDecimal minPaid, BigDecimal extraPaid) {
        DebtMonthResult r = new DebtMonthResult();

        BigDecimal principal = BigDecimal.valueOf(d.getPrincipalAmount());
        BigDecimal rateMonthly = BigDecimal.valueOf(d.getInterestRate())
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // 1) interest
        BigDecimal interest = principal.multiply(rateMonthly).setScale(2, RoundingMode.HALF_UP);
        principal = principal.add(interest);
        r.interestAdded = interest;

        // 2) EMI pays only min (extra ignored for MVP)
        BigDecimal minApplied = minPaid.min(principal);
        principal = principal.subtract(minApplied);
        r.minPaidApplied = minApplied;

        r.extraPaidApplied = BigDecimal.ZERO; // ignore
        d.setPrincipalAmount(principal.doubleValue());
        return r;
    }
}
