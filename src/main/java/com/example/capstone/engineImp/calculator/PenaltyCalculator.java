package com.example.capstone.engineImp.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PenaltyCalculator {

    public static BigDecimal calculateMonthly(
            BigDecimal principal,
            BigDecimal penaltyRate
    ) {
        return principal
                .multiply(penaltyRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateDaily(
            BigDecimal principal,
            BigDecimal penaltyRate,
            int days
    ) {
        return principal
                .multiply(penaltyRate.divide(BigDecimal.valueOf(36000), 10, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
    }
}