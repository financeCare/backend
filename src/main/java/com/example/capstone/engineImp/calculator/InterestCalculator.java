package com.example.capstone.engineImp.calculator;

import com.example.capstone.enums.InterestCalculationType;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.example.capstone.enums.InterestCalculationType.*;

public class InterestCalculator {

    public static BigDecimal calculate(
            BigDecimal principal,
            BigDecimal annualRate,
            InterestCalculationType type
    ) {

        return switch (type) {
            case THIRTY_360 -> principal
                    .multiply(annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
            case DAILY_SIMPLE -> principal
                    .multiply(annualRate.divide(BigDecimal.valueOf(360), 10, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(30))
                    .setScale(2, RoundingMode.HALF_UP);
            case FLAT_RATE -> principal
                    .multiply(annualRate)
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> throw new IllegalStateException();
        };

    }
}
