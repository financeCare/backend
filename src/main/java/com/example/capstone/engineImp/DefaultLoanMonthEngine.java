package com.example.capstone.engineImp;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.engineImp.calculator.InterestCalculator;
import com.example.capstone.engineInterface.LoanMonthEngine;
import com.example.capstone.util.PenaltyLogic;
import com.example.capstone.enums.InterestCalculationType;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DefaultLoanMonthEngine implements LoanMonthEngine {

    @Override
    public LoanMonthResult simulateMonth(
            DebtSim debt,
            BigDecimal extraPayment) {

        LoanMonthResult loanMonthResult = new LoanMonthResult();
        loanMonthResult.principalStart = debt.getPrincipal();

        BigDecimal interest = InterestCalculator.calculate(
                debt.getPrincipal(),
                debt.getAnnualInterestRate(),
                debt.getInterestType());

        BigDecimal principal = debt.getPrincipal();

        if (debt.getInterestType() == InterestCalculationType.DAILY_COMPOUND) {
            principal = principal.add(interest);
        }

        // Check for penalty using unified logic
        PenaltyLogic.LateResult lateResult = PenaltyLogic.checkAndCalculate(debt, principal);
        BigDecimal penalty = lateResult.getPenalty();

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
            if (debt.getInterestType() == InterestCalculationType.DAILY_COMPOUND) {
                principal = principal.add(penalty);
            }
        }

        BigDecimal minPaid = debt.getMinPayment();
        BigDecimal totalPaid = minPaid.add(extraPayment);

        principal = principal.subtract(totalPaid);

        if (principal.compareTo(BigDecimal.ZERO) < 0) {
            principal = BigDecimal.ZERO;
        }

        debt.setPrincipal(principal);

        loanMonthResult.interest = interest;
        loanMonthResult.penaltyInterest = penalty;
        loanMonthResult.minPaid = minPaid;
        loanMonthResult.extraPaid = extraPayment;
        loanMonthResult.principalEnd = principal;
        loanMonthResult.late = lateResult.isLate();

        return loanMonthResult;
    }
}