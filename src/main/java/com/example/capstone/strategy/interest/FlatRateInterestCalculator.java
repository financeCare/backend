package com.example.capstone.strategy.interest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component("FLAT_RATE")
public class FlatRateInterestCalculator implements InterestCalculator {

    @Override
    public BigDecimal calculate(BigDecimal principal,
                                BigDecimal annualRate,
                                int daysInPeriod) {

        BigDecimal monthlyRate =
                annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        return principal.multiply(monthlyRate);
    }
}