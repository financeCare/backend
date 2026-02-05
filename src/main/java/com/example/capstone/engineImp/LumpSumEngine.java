package com.example.capstone.engineImp;

import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.engineInterface.DebtMonthResult;
import com.example.capstone.entity.Debt;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LumpSumEngine implements DebtMonthEngine {

    @Override
    public DebtMonthResult runMonth(Debt d, BigDecimal minPaid, BigDecimal extraPaid) {
        DebtMonthResult r = new DebtMonthResult();

        BigDecimal principal = BigDecimal.valueOf(d.getPrincipalAmount());
        BigDecimal rateMonthly = BigDecimal.valueOf(d.getInterestRate())
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // interest
        BigDecimal interest = principal.multiply(rateMonthly).setScale(2, RoundingMode.HALF_UP);
        principal = principal.add(interest);
        r.interestAdded = interest;

        // pay (normally 0 until final)
        BigDecimal pay = minPaid.add(extraPaid).min(principal);
        principal = principal.subtract(pay);

        // treat all as extra to keep semantics
        r.minPaidApplied = BigDecimal.ZERO;
        r.extraPaidApplied = pay;

        d.setPrincipalAmount(principal.doubleValue());
        return r;
    }
}
