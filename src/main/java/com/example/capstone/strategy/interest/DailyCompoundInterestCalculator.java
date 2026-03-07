package com.example.capstone.strategy.interest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

@Component("DAILY_COMPOUND")
public class DailyCompoundInterestCalculator implements InterestCalculator {

    private static final MathContext MC = new MathContext(15);

    @Override
    public BigDecimal calculate(BigDecimal principal,
                                BigDecimal annualRate,
                                int daysInPeriod) {

        BigDecimal dailyRate = annualRate.divide(BigDecimal.valueOf(365), MC);

        BigDecimal factor = BigDecimal.ONE
                .add(dailyRate)
                .pow(daysInPeriod, MC);

        BigDecimal compounded = principal.multiply(factor, MC);

        return compounded.subtract(principal);
    }
}