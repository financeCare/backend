package com.example.capstone.engineImp.repaymentType;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.engineImp.calculator.InterestCalculator;
import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.factory.InterestCalculatorFactory;
import com.example.capstone.util.PenaltyLogic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class EMIEngine implements DebtMonthEngine {

    private final InterestCalculatorFactory interestFactory;

    @Override
    public boolean supports(String repaymentType) {
        return repaymentType.equals("EMI");
    }

    @Override
    public LoanMonthResult runMonth(
            DebtSim debt,
            BigDecimal minPayment,
            BigDecimal extraPayment
    ) {

        BigDecimal principalStart = debt.getPrincipal();

        BigDecimal interest = InterestCalculator.calculate(
                        principalStart,
                        debt.getAnnualInterestRate(),
                        debt.getInterestType()
                );

        BigDecimal totalPayment = minPayment.add(extraPayment);

        // Check for penalty
        PenaltyLogic.LateResult lateResult = PenaltyLogic.checkAndCalculate(debt, principalStart);
        BigDecimal penalty = lateResult.getPenalty();

        BigDecimal principalPaid = totalPayment.subtract(interest).subtract(penalty);

        if (principalPaid.compareTo(BigDecimal.ZERO) < 0) {
            principalPaid = BigDecimal.ZERO;
        }

        BigDecimal principalEnd = principalStart.subtract(principalPaid).add(penalty);

        if (principalEnd.compareTo(BigDecimal.ZERO) < 0) {
            principalEnd = BigDecimal.ZERO;
        }

        debt.setPrincipal(principalEnd);

        return new LoanMonthResult(
                principalStart,
                interest,
                penalty,
                minPayment,
                extraPayment,
                principalEnd,
                lateResult.isLate(),
                false
        );
    }
}