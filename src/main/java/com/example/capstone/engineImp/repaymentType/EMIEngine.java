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
public class EMIEngine implements DebtMonthEngine {

    @Override
    public boolean supports(String repaymentType) {
        return repaymentType.equals("EMI");
    }

    @Override
    public LoanMonthResult runMonth(
            DebtSim debt,
            BigDecimal minPayment,
            BigDecimal extraPayment) {

        BigDecimal principalStart = debt.getPrincipal();

        BigDecimal interest = InterestCalculator.calculate(
                principalStart,
                debt.getAnnualInterestRate(),
                debt.getInterestType());

        BigDecimal totalPlannedPayment = minPayment.add(extraPayment);

        // Check for penalty
        PenaltyLogic.LateResult lateResult = PenaltyLogic.checkAndCalculate(debt, principalStart, totalPlannedPayment);
        BigDecimal penalty = lateResult.getPenalty();

        // Calculate max needed to clear the debt this month (include outstanding charges)
        BigDecimal outstandingCharges = debt.getInterestOutstanding()
                .add(debt.getLateFeeOutstanding())
                .add(debt.getPenaltyOutstanding());
        
        BigDecimal maxNeeded = principalStart.add(interest).add(penalty).add(outstandingCharges);
        BigDecimal actualTotalPayment = totalPlannedPayment.min(maxNeeded);

        // Track how much is paid to each part
        BigDecimal remainingToAllocate = actualTotalPayment;

        // 1. Pay outstanding charges first (Initial balances)
        BigDecimal paidToOldCharges = remainingToAllocate.min(outstandingCharges);
        remainingToAllocate = remainingToAllocate.subtract(paidToOldCharges);
        
        // Update outstanding charges for next month in simulation
        BigDecimal p = debt.getPenaltyOutstanding();
        BigDecimal pPaid = paidToOldCharges.min(p);
        debt.setPenaltyOutstanding(p.subtract(pPaid));
        
        BigDecimal l = debt.getLateFeeOutstanding();
        BigDecimal lPaid = (paidToOldCharges.subtract(pPaid)).min(l);
        debt.setLateFeeOutstanding(l.subtract(lPaid));
        
        BigDecimal iIdx = debt.getInterestOutstanding();
        BigDecimal iIdxPaid = (paidToOldCharges.subtract(pPaid).subtract(lPaid)).min(iIdx);
        debt.setInterestOutstanding(iIdx.subtract(iIdxPaid));

        // 2. Pay current month charges
        BigDecimal currentMonthCharges = interest.add(penalty);
        BigDecimal paidToCurrentCharges = remainingToAllocate.min(currentMonthCharges);
        remainingToAllocate = remainingToAllocate.subtract(paidToCurrentCharges);

        // 3. Pay principal
        BigDecimal principalPaid = remainingToAllocate;
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