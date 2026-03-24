package com.example.capstone.engineImp.repaymentType;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.engineImp.calculator.InterestCalculator;
import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.util.PenaltyLogic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BulletEngine implements DebtMonthEngine {

    @Override
    public boolean supports(String repaymentType) {
        return repaymentType.equals("BULLET");
    }

    @Override
    public LoanMonthResult runMonth(
            DebtSim debt,
            BigDecimal minPayment,
            BigDecimal extraPayment) {

        BigDecimal principalStart = debt.getPrincipal();
        BigDecimal interest = InterestCalculator
                .calculate(principalStart, debt.getAnnualInterestRate(), debt.getInterestType());

        BigDecimal totalPlannedPayment = minPayment.add(extraPayment);

        // Check for penalty
        PenaltyLogic.LateResult lateResult = PenaltyLogic.checkAndCalculate(debt, principalStart, totalPlannedPayment);
        BigDecimal penalty = lateResult.getPenalty();

        // Calculate max needed to clear the debt this month
        BigDecimal maxNeeded = principalStart.add(interest).add(penalty);
        BigDecimal actualTotalPayment = totalPlannedPayment.min(maxNeeded);

        // Distribute actual payment back to min and extra for reporting
        BigDecimal actualMinPaid = minPayment.min(actualTotalPayment);
        BigDecimal actualExtraPaid = actualTotalPayment.subtract(actualMinPaid);

        BigDecimal principalPaid = actualTotalPayment.subtract(interest).subtract(penalty);
        BigDecimal principalEnd = principalStart.subtract(principalPaid);

        if (principalEnd.compareTo(BigDecimal.ZERO) < 0) {
            principalEnd = BigDecimal.ZERO;
        }

        debt.setPrincipal(principalEnd);

        return new LoanMonthResult(
                principalStart,
                interest,
                penalty,
                actualMinPaid,
                actualExtraPaid,
                principalEnd,
                lateResult.isLate(),
                false);
    }
}