package com.example.capstone.engine;

import com.example.capstone.domain.DebtSim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PenaltyEngine {

    public BigDecimal calculatePenalty(
            DebtSim debt,
            BigDecimal minPayment,
            BigDecimal actualPayment
    ) {
        if (actualPayment.compareTo(minPayment) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal missing = minPayment.subtract(actualPayment);
        BigDecimal penaltyRate = (debt != null && debt.getPenaltyAnnualRate() != null)
                ? debt.getPenaltyAnnualRate()
                : new BigDecimal("0.02");

        return missing.multiply(penaltyRate);
    }
}