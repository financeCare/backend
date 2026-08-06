package com.example.capstone.strategy.interest;

import java.math.BigDecimal;

public interface InterestCalculator {

    BigDecimal calculate(
            BigDecimal principal,
            BigDecimal annualRate,
            int daysInPeriod
    );
}