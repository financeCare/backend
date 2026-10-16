package com.example.capstone.service;

import com.example.capstone.allocation.DefaultBudgetAllocator;
import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.dto.*;
import com.example.capstone.factory.DebtEngineFactory;
import com.example.capstone.engine.interfaces.DebtMonthEngine;
import com.example.capstone.strategy.repayment.RepaymentStrategyInterface;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.example.capstone.exception.BusinessException;

import java.math.BigDecimal;
import java.util.*;

@Service
@AllArgsConstructor
public class RepaymentPlanSimulator {

    private static final int MAX_MONTHS = 600;

    private final DefaultBudgetAllocator allocator;
    private final DebtEngineFactory debtEngineFactory;

    public PlanResultDTO simulateCore(
            RepaymentPlanDtoV2 plan,
            List<DebtSim> originalDebts,
            RepaymentStrategyInterface strategy) {

        List<DebtSim> debts = new ArrayList<>(originalDebts);
        Map<UUID, Integer> consecutiveLateMonths = new HashMap<>();
        int month = 0;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        List<MonthlyPlanResultDTO> monthlyResults = new ArrayList<>();

        while (!debts.isEmpty()) {
            if (month >= MAX_MONTHS) {
                throw new BusinessException("Simulation exceeds 50 years", HttpStatus.BAD_REQUEST);
            }

            month++;
            updateDebtDates(debts, month);

            DebtSim target = strategy.apply(debts);
            Map<UUID, BigDecimal> extraMap = allocator.allocate(plan.getMonthlyBudget(), debts, target);

            MonthlyStepResult stepResult = processMonthStep(debts, extraMap, consecutiveLateMonths);

            totalInterest = totalInterest.add(stepResult.monthInterest());
            totalPaid = totalPaid.add(stepResult.paidThisMonth());

            debts.removeIf(d -> d.getPrincipal().compareTo(BigDecimal.ZERO) <= 0);

            BigDecimal remainingTotal = debts.stream()
                    .map(DebtSim::getPrincipal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            MonthlyPlanResultDTO m = new MonthlyPlanResultDTO();
            m.setMonthNo(month);
            m.setMonthInterest(stepResult.monthInterest());
            m.setPaidThisMonth(stepResult.paidThisMonth());
            m.setRemainingDebtTotal(remainingTotal);
            m.setDebtPayments(stepResult.debtPayments());

            monthlyResults.add(m);
        }

        PlanResultDTO result = new PlanResultDTO();
        result.setEstimatedMonths(month);
        result.setTotalInterest(totalInterest);
        result.setTotalPaid(totalPaid);
        result.setMonthlyResults(monthlyResults);

        return result;
    }

    private void updateDebtDates(List<DebtSim> debts, int month) {
        for (DebtSim d : debts) {
            if (d.getStartDate() != null) {
                d.setCurrentDate(d.getStartDate().plusMonths((long) month - 1));
            }
        }
    }

    private MonthlyStepResult processMonthStep(
            List<DebtSim> debts,
            Map<UUID, BigDecimal> extraMap,
            Map<UUID, Integer> consecutiveLateMonths) {

        List<DebtPaymentDTO> debtPayments = new ArrayList<>();
        BigDecimal monthInterest = BigDecimal.ZERO;
        BigDecimal paidThisMonth = BigDecimal.ZERO;

        for (DebtSim d : debts) {
            DebtMonthEngine engine = debtEngineFactory.getEngine(d.getRepaymentType().toString());
            BigDecimal totalAllocated = extraMap.getOrDefault(d.getDebtId(), BigDecimal.ZERO);

            BigDecimal minPaid = totalAllocated.min(d.getMinPayment());
            BigDecimal extra = totalAllocated.subtract(minPaid);

            LoanMonthResult r = engine.runMonth(d, minPaid, extra);

            updateNplStatus(d, r, consecutiveLateMonths);

            d.setPrincipal(r.getPrincipalEnd());
            monthInterest = monthInterest.add(r.getInterest());

            BigDecimal paid = r.getMinPaid().add(r.getExtraPaid());
            paidThisMonth = paidThisMonth.add(paid);

            DebtPaymentDTO dto = new DebtPaymentDTO();
            dto.setDebtId(d.getDebtId());
            dto.setDebtName(d.getDebtName());
            dto.setBeforeBalance(r.getPrincipalStart());
            dto.setInterestAdded(r.getInterest());
            dto.setMinPaid(r.getMinPaid());
            dto.setExtraPaid(r.getExtraPaid());
            dto.setAfterBalance(r.getPrincipalEnd());
            dto.setDefaulted(d.isDefaulted());
            dto.setNplRisk(consecutiveLateMonths.getOrDefault(d.getDebtId(), 0) >= 2);

            debtPayments.add(dto);
        }

        return new MonthlyStepResult(monthInterest, paidThisMonth, debtPayments);
    }

    private void updateNplStatus(DebtSim d, LoanMonthResult r, Map<UUID, Integer> consecutiveLateMonths) {
        if (r.isLate()) {
            int count = consecutiveLateMonths.getOrDefault(d.getDebtId(), 0) + 1;
            consecutiveLateMonths.put(d.getDebtId(), count);
            if (count >= 3) {
                d.setDefaulted(true);
            }
        } else {
            consecutiveLateMonths.put(d.getDebtId(), 0);
        }
    }

    private record MonthlyStepResult(BigDecimal monthInterest, BigDecimal paidThisMonth, List<DebtPaymentDTO> debtPayments) {}
}