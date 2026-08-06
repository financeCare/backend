package com.example.capstone.strategy.interest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component("THIRTY_360")
public class Thirty360InterestCalculator implements InterestCalculator {

    @Override
    public BigDecimal calculate(BigDecimal principal,
                                BigDecimal annualRate,
                                int daysInPeriod) {

        BigDecimal dailyRate = annualRate
                .divide(BigDecimal.valueOf(360), 10, RoundingMode.HALF_UP);

        return principal
                .multiply(dailyRate)
                .multiply(BigDecimal.valueOf(daysInPeriod));
    }
}