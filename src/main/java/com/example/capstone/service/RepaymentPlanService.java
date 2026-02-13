package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.engineFactory.DebtEngineFactory;
import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.engineInterface.DebtMonthResult;
import com.example.capstone.entity.*;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.*;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RepaymentPlanService {
    private final RepaymentPlanRepository repaymentPlanRepository;
    private final DebtRepository debtRepository;
    private final RepaymentStrategyRepository repaymentStrategyRepository;
    private final UserService userService;

    private final DebtEngineFactory engineFactory = new DebtEngineFactory();

    public RepaymentStrategy createRepaymentStrategy(RepaymentStrategyDTO repaymentStrategyDTO) {
        RepaymentStrategy strategy = new RepaymentStrategy();
        strategy.setStrategyName(repaymentStrategyDTO.getStrategyName());
        strategy.setDescription(repaymentStrategyDTO.getDescription());
        strategy.setIsActive(true);
        try {
            repaymentStrategyRepository.save(strategy);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage(), HttpStatus.NOT_FOUND);
        }
        return strategy;
    }

    public RepaymentStrategyDtoResponse getAllRepaymentStrategies(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Debt> debts = debtRepository.findByIsActiveAndUserId(true,userId);
        BigDecimal minSum = debts.stream()
                .map(d -> BigDecimal.valueOf(Math.max(0, d.getMinPayment())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RepaymentStrategyDtoResponse(minSum.doubleValue(),repaymentStrategyRepository.findAll());
    }

    public String deleteRepaymentStrategy(UUID strategyId) {
        repaymentStrategyRepository.findById(strategyId).ifPresent(repaymentStrategyRepository::delete);
        return "Repayment Strategy id " + strategyId + " delete successfully";
    }

    //TODO : helper function for simulate Repayment Plan

    private int resolveRepaymentTypeId(Debt d) {
        if (d.getRepaymentType() == null) {
            throw new IllegalStateException("Debt " + d.getDebtId() + " missing repaymentType");
        }
        return d.getRepaymentType().getRepaymentTypeId();
    }

    private DebtSim selectTarget(
            List<DebtSim> debts,
            UUID strategyId
    ) {

        RepaymentStrategy strategy = repaymentStrategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found"));

        String name = strategy.getStrategyName();

        // ❌ ไม่โปะ
        if ("MINIMUM_ONLY".equalsIgnoreCase(name)) {
            return null;
        }

        // 🔵 Snowball: ยอดคงเหลือน้อยที่สุด
        if ("SNOWBALL".equalsIgnoreCase(name)) {
            return debts.stream()
                    .filter(DebtSim::isActive)
                    .min(Comparator.comparing(DebtSim::getPrincipalAmount))
                    .orElse(null);
        }

        // 🔴 Avalanche: ดอกเบี้ยสูงสุด
        if ("AVALANCHE".equalsIgnoreCase(name)) {
            return debts.stream()
                    .filter(DebtSim::isActive)
                    .max(Comparator.comparing(DebtSim::getInterestRate))
                    .orElse(null);
        }

        // 🟣 Hybrid: ดอกสูง แต่ยอดไม่เกิน median
        if ("HYBRID".equalsIgnoreCase(name)) {

            double median = debts.stream()
                    .mapToDouble(DebtSim::getPrincipalAmount)
                    .sorted()
                    .skip(debts.size() / 2)
                    .findFirst()
                    .orElse(Double.MAX_VALUE);

            return debts.stream()
                    .filter(DebtSim::isActive)
                    .filter(d -> d.getPrincipalAmount() <= median)
                    .max(Comparator.comparing(DebtSim::getInterestRate))
                    .orElse(null);
        }
        throw new IllegalStateException("Unsupported strategy: " + name);
    }

    private BigDecimal sumMinPayments(List<Debt> debts) {
        return debts.stream()
                .map(d -> BigDecimal.valueOf(d.getMinPayment()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void closePaidDebts(List<DebtSim> debts) {
        for (DebtSim d : debts) {
            if (d.getPrincipalAmount() <= 0.000001) {
                d.setActive(false);
            }
        }
        debts.removeIf(d -> !d.isActive());
    }

    //TODO : end helper simulator for Repayment Plan function

    //TODO : Simulate Repayment Plan function
    @Transactional()
    public PlanResultDTO simulate(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        RepaymentPlan plan = repaymentPlanRepository.findByUserId(userId);
        if (plan == null) {
            throw new BusinessException("Repayment plan not found for user", HttpStatus.NOT_FOUND);
        }
        List<Debt> debts = debtRepository.findByIsActiveAndUserId(true,userId);
        if (debts.isEmpty()) {
            return new PlanResultDTO(0, 0.0, 0.0, List.of());
        }
        List<DebtSim> simDebts = debts.stream().map(d -> {
            DebtSim s = new DebtSim();
            s.debtId = d.getDebtId();
            s.debtName = d.getDebtName();
            s.principalAmount = d.getPrincipalAmount();
            s.interestRate = d.getInterestRate();
            s.minPayment = d.getMinPayment();
            s.isActive = d.isActive();
            s.setRepaymentTypeId(resolveRepaymentTypeId(d));
            return s;
        }).collect(Collectors.toList());
        return simulateCore(plan, simDebts);
    }


    private PlanResultDTO simulateCore(RepaymentPlan plan, List<DebtSim> debts) {
        BigDecimal monthlyBudget = BigDecimal.valueOf(plan.getMonthlyBudget());
        int month = 0;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        List<MonthlyPlanResultDTO> monthlyResults = new ArrayList<>();

        while (!debts.isEmpty()) {
            month++;

            // 1) คำนวณยอดขั้นต่ำ (เฉพาะหนี้ที่มี minPayment > 0)
            BigDecimal minSum = debts.stream()
                    .map(d -> BigDecimal.valueOf(Math.max(0, d.getMinPayment())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (monthlyBudget.compareTo(minSum) < 0) {
                throw new BusinessException("Monthly budget is not enough. Need at least " + minSum, HttpStatus.BAD_REQUEST);
            }

            BigDecimal remaining = monthlyBudget.subtract(minSum);

            // 2) เลือก target สำหรับ extra (ใช้ strategyId ของแผน หรือใช้ priority ก็ได้)
            DebtSim target = selectTarget(debts, plan.getStrategyId());

            // 3) Run month per debt แล้วเก็บรายงาน
            List<DebtPaymentDTO> debtPayments = new ArrayList<>();
            BigDecimal monthInterest = BigDecimal.ZERO;
            BigDecimal paidThisMonth = BigDecimal.ZERO;

            for (DebtSim d : debts) {
                int typeId = d.repaymentTypeId;

                BigDecimal minPaid = BigDecimal.valueOf(Math.max(0, d.getMinPayment()));
                BigDecimal extraPaid = BigDecimal.ZERO;

                if (target != null && Objects.equals(d.getDebtId(), target.getDebtId())) {
                    extraPaid = remaining;
                }

                double before = d.getPrincipalAmount();

                DebtMonthEngine engine = engineFactory.get(typeId);
                DebtMonthResult r = engine.runMonth(d, minPaid, extraPaid);

                monthInterest = monthInterest.add(r.interestAdded);
                paidThisMonth = paidThisMonth.add(r.minPaidApplied).add(r.extraPaidApplied);

                debtPayments.add(new DebtPaymentDTO(
                        d.getDebtId(),
                        d.getDebtName(),
                        before,
                        r.interestAdded.doubleValue(),
                        r.minPaidApplied.doubleValue(),
                        r.extraPaidApplied.doubleValue(),
                        d.getPrincipalAmount()
                ));
            }

            totalInterest = totalInterest.add(monthInterest);
            totalPaid = totalPaid.add(paidThisMonth);

            // 4) ปิดหนี้
            closePaidDebts(debts);

            // 5) รวมยอดคงเหลือ
            double remainingTotal = debts.stream().mapToDouble(DebtSim::getPrincipalAmount).sum();

            monthlyResults.add(new MonthlyPlanResultDTO(
                    month,
                    monthInterest.doubleValue(),
                    paidThisMonth.doubleValue(),
                    remainingTotal,
                    debtPayments
            ));

            if (month > 600) throw new IllegalStateException("Simulation exceeds 50 years");
        }

        return new PlanResultDTO(month, totalInterest.doubleValue(), totalPaid.doubleValue(), monthlyResults);
    }
    //TODO : end Simulate Repayment Plan function

    //TODO : Create Repayment Plan function
    public RepaymentPlan changeRepaymentPlan(UUID userId, double monthlyBudget, UUID strategyId) {
        RepaymentPlan repaymentPlan = repaymentPlanRepository.findByUserId(userId);
        if (repaymentPlan != null) {
            repaymentPlan.setMonthlyBudget(monthlyBudget);
            repaymentPlan.setStrategyId(strategyId);
            return repaymentPlanRepository.save(repaymentPlan);
        }else{
            RepaymentPlan plan = new RepaymentPlan();
            plan.setPlanId(UUID.randomUUID());
            plan.setUserId(userId);
            plan.setMonthlyBudget(monthlyBudget);
            plan.setStrategyId(strategyId);
            return repaymentPlanRepository.save(plan);
        }
    }

    public BigDecimal sumMinPaymentsForUser(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Debt> debts = debtRepository.findByIsActiveAndUserId(true, userId);
        return sumMinPayments(debts);
    }

}