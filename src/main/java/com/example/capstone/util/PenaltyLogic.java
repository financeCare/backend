package com.example.capstone.util;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.engineImp.calculator.PenaltyCalculator;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class PenaltyLogic {

    @Data
    public static class LateResult {
        private final boolean late;
        private final BigDecimal penalty;
    }

    public static LateResult checkAndCalculate(DebtSim debt, BigDecimal principal) {
        if (debt.getCurrentDate() == null) {
            return new LateResult(false, BigDecimal.ZERO);
        }

        // Logic assumes payment is made at the end of the month for simulation purposes
        LocalDate dueDate = debt.getCurrentDate()
                .withDayOfMonth(
                        Math.min(debt.getDueDay(),
                                debt.getCurrentDate().lengthOfMonth())
                );

        LocalDate paymentDate = debt.getCurrentDate()
                .withDayOfMonth(debt.getCurrentDate().lengthOfMonth());

        LocalDate graceEnd = dueDate.plusDays(debt.getGracePeriodDays());

        boolean late = paymentDate.isAfter(graceEnd);
        BigDecimal penalty = BigDecimal.ZERO;

        if (late) {
            long overdueDays = ChronoUnit.DAYS.between(dueDate, paymentDate);
            if (overdueDays > debt.getPenaltyTriggerDays() && debt.getPenaltyAnnualRate() != null) {
                penalty = PenaltyCalculator.calculateMonthly(
                        principal,
                        debt.getPenaltyAnnualRate()
                );
            }
        }

        return new LateResult(late, penalty);
    }
}
