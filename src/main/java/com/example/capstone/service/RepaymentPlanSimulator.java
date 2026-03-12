package com.example.capstone.service;

import com.example.capstone.allocation.DefaultBudgetAllocator;
import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.dto.*;
import com.example.capstone.factory.DebtEngineFactory;
import com.example.capstone.engineInterface.DebtMonthEngine;
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

                int month = 0;

                BigDecimal totalInterest = BigDecimal.ZERO;
                BigDecimal totalPaid = BigDecimal.ZERO;

                List<MonthlyPlanResultDTO> monthlyResults = new ArrayList<>();

                while (!debts.isEmpty()) {

                        if (month >= MAX_MONTHS) {
                                throw new BusinessException("Simulation exceeds 50 years", HttpStatus.BAD_REQUEST);
                        }

                        month++;

                        // Update current date for all debts for penalty calculation
                        for (DebtSim d : debts) {
                            if (d.getStartDate() != null) {
                                d.setCurrentDate(d.getStartDate().plusMonths(month - 1));
                            }
                        }

                        DebtSim target = strategy.apply(debts);

                        Map<UUID, BigDecimal> extraMap = allocator.allocate(plan.getMonthlyBudget(), debts, target);

                        List<DebtPaymentDTO> debtPayments = new ArrayList<>();

                        BigDecimal monthInterest = BigDecimal.ZERO;
                        BigDecimal paidThisMonth = BigDecimal.ZERO;

                        for (DebtSim d : debts) {

                                DebtMonthEngine engine = debtEngineFactory.getEngine(d.getRepaymentType().toString());

                                BigDecimal totalAllocated = extraMap.getOrDefault(d.getDebtId(), BigDecimal.ZERO);

                                // Split into min and extra for the engine
                                BigDecimal minPaid = totalAllocated.min(d.getMinPayment());
                                BigDecimal extra = totalAllocated.subtract(minPaid);

                                LoanMonthResult r = engine.runMonth(d, minPaid, extra);

                                d.setPrincipal(r.getPrincipalEnd()); // Update current principal
                                monthInterest = monthInterest.add(r.getInterest());

                                BigDecimal paid = r.getMinPaid().add(r.getExtraPaid());

                                paidThisMonth = paidThisMonth.add(paid);

                                DebtPaymentDTO dto = new DebtPaymentDTO();

                                dto.setDebtId(d.getDebtId());
                                dto.setDebtName(d.getDebtName());
                                dto.setPrincipalStart(r.getPrincipalStart());
                                dto.setInterest(r.getInterest());
                                dto.setPaid(paid);
                                dto.setPrincipalEnd(r.getPrincipalEnd());

                                debtPayments.add(dto);
                        }

                        totalInterest = totalInterest.add(monthInterest);
                        totalPaid = totalPaid.add(paidThisMonth);

                        debts.removeIf(d -> d.getPrincipal().compareTo(BigDecimal.ZERO) <= 0);

                        BigDecimal remainingTotal = debts.stream()
                                        .map(DebtSim::getPrincipal)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        MonthlyPlanResultDTO m = new MonthlyPlanResultDTO();

                        m.setMonth(month);
                        m.setTotalInterest(monthInterest);
                        m.setTotalPaid(paidThisMonth);
                        m.setRemainingTotal(remainingTotal);
                        m.setDebtPayments(debtPayments);

                        monthlyResults.add(m);
                }

                PlanResultDTO result = new PlanResultDTO();

                result.setTotalMonths(month);
                result.setTotalInterest(totalInterest);
                result.setTotalPaid(totalPaid);
                result.setMonthlyResults(monthlyResults);

                return result;
        }
}