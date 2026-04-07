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

        BigDecimal principalStart = debt.getPrincipal();

        BigDecimal interest = InterestCalculator.calculate(
                principalStart,
                debt.getAnnualInterestRate(),
                debt.getInterestType());

        BigDecimal totalPaid = debt.getMinPayment().add(extraPayment);

        // Check for penalty using unified logic
        PenaltyLogic.LateResult lateResult = PenaltyLogic.checkAndCalculate(debt, totalPaid);
        BigDecimal penalty = lateResult.getPenalty();

        // Target: Pay Interest and Penalty first (Waterfall)
        BigDecimal currentCharges = interest.add(penalty);
        BigDecimal remainingToPay = totalPaid;

        // 1. Pay charges
        BigDecimal paidToCharges = remainingToPay.min(currentCharges);
        remainingToPay = remainingToPay.subtract(paidToCharges);

        // 2. Pay principal with the rest
        BigDecimal principalPaid = remainingToPay.min(principalStart);
        BigDecimal principalEnd = principalStart.subtract(principalPaid);

        if (principalEnd.compareTo(BigDecimal.ZERO) < 0) {
            principalEnd = BigDecimal.ZERO;
        }

        debt.setPrincipal(principalEnd);

        LoanMonthResult loanMonthResult = new LoanMonthResult();
        loanMonthResult.interest = interest;
        loanMonthResult.penaltyInterest = penalty;
        loanMonthResult.minPaid = debt.getMinPayment().min(totalPaid);
        loanMonthResult.extraPaid = totalPaid.subtract(loanMonthResult.minPaid);
        loanMonthResult.principalEnd = principalEnd;
        loanMonthResult.principalStart = principalStart;
        loanMonthResult.late = lateResult.isLate();

        return loanMonthResult;
    }
}