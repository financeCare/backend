package com.example.capstone.service;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.dto.*;
import com.example.capstone.enums.RepaymentTypeEnum;
import com.example.capstone.entity.*;
import com.example.capstone.entity.RepaymentPlan;
import com.example.capstone.enums.StrategyType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.factory.StrategyFactory;
import com.example.capstone.repository.*;
import com.example.capstone.strategy.repayment.RepaymentStrategyInterface;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@AllArgsConstructor
public class RepaymentPlanService {
    private final RepaymentPlanRepository repaymentPlanRepository;
    private final DebtRepository debtRepository;
    private final RepaymentStrategyRepository repaymentStrategyRepository;
    private final UserService userService;
    private final RepaymentPlanSimulator repaymentPlanSimulator;
    private final StrategyFactory strategyFactory;

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
        List<Debt> debts = debtRepository.findByActiveAndUserId(true,userId);
        BigDecimal minSum = debts.stream()
                .map(d -> BigDecimal.valueOf(Math.max(0, d.getMinPayment().intValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RepaymentStrategyDtoResponse(minSum.doubleValue(),repaymentStrategyRepository.findAll());
    }

    public String deleteRepaymentStrategy(UUID strategyId) {
        repaymentStrategyRepository.findById(strategyId).ifPresent(repaymentStrategyRepository::delete);
        return "Repayment Strategy id " + strategyId + " delete successfully";
    }

    //TODO : Create Repayment Plan function
    public RepaymentPlan changeRepaymentPlan(UUID userId, BigDecimal monthlyBudget, UUID strategyId) {
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

    @Transactional(readOnly = true)
    public PlanResultDTO simulate(String token) {

        UUID userId = userService.extractUserIdFromToken(token);

        RepaymentPlan planEntity =
                repaymentPlanRepository.findByUserId(userId);

        if (planEntity == null) {
            throw new BusinessException(
                    "Repayment plan not found",
                    HttpStatus.NOT_FOUND
            );
        }

        List<Debt> debtEntities =
                debtRepository.findByActiveAndUserId(true, userId);

        if (debtEntities.isEmpty()) {
            return new PlanResultDTO(
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new ArrayList<>()
            );
        }

        RepaymentPlanDtoV2 simPlan = mapToDomainPlan(planEntity);

        List<DebtSim> simDebts =
                debtEntities.stream()
                        .map(this::mapToDomainDebt)
                        .toList();

        RepaymentStrategyInterface strategy =
                strategyFactory.getStrategy(simPlan.getStrategyType());

        return repaymentPlanSimulator.simulateCore(simPlan, simDebts, strategy);
    }

    private RepaymentPlanDtoV2 mapToDomainPlan(RepaymentPlan entity) {

        if (entity == null) {
            throw new IllegalArgumentException("RepaymentPlanEntity must not be null");
        }

        if (entity.getStrategyId() == null) {
            throw new IllegalStateException("Strategy is not configured for this plan");
        }

        RepaymentPlanDtoV2 plan = new RepaymentPlanDtoV2();

        // Budget
        plan.setMonthlyBudget(
                entity.getMonthlyBudget() != null
                        ? entity.getMonthlyBudget()
                        : BigDecimal.ZERO
        );

        // Strategy Mapping (Entity → Domain Enum)
        String strategyName = repaymentStrategyRepository.findById(entity.getStrategyId()).orElseThrow(
                () -> new IllegalArgumentException("Strategy not found")).getStrategyName();

        try {
            plan.setStrategyType(StrategyType.valueOf(strategyName));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Unknown strategy type in database: " + strategyName
            );
        }

        return plan;
    }
    private DebtSim mapToDomainDebt(Debt entity) {

        if (entity == null) {
            throw new IllegalArgumentException("Debt entity must not be null");
        }

        DebtSim debtSim = new DebtSim();

        debtSim.setDebtId(entity.getDebtId());
        debtSim.setDebtName(entity.getDebtName());

        // Principal
        debtSim.setPrincipal(
                entity.getPrincipalAmount() != null
                        ? entity.getPrincipalAmount()
                        : BigDecimal.ZERO
        );

        // Interest Rate (normalize % → decimal)
        debtSim.setAnnualInterestRate(normalizeRate(entity.getInterestRate()));

        // Interest Type (รองรับ DAILY_COMPOUND)
        debtSim.setInterestType(entity.getInterestCalculationType());

        // Min Payment
        debtSim.setMinPayment(
                entity.getMinPayment() != null
                        ? entity.getMinPayment()
                        : BigDecimal.ZERO
        );

        debtSim.setActive(entity.getActive());
        debtSim.setRepaymentType(RepaymentTypeEnum.valueOf(entity.getRepaymentType().getRepaymentTypeName()));

        // New fields for penalty and late detection
        debtSim.setDueDay(entity.getDueDay());
        debtSim.setGracePeriodDays(entity.getGracePeriodDays());
        debtSim.setPenaltyTriggerDays(entity.getPenaltyTriggerDays());
        debtSim.setPenaltyAnnualRate(normalizeRate(entity.getPenaltyAnnualRate()));
        debtSim.setDefaulted(entity.getDefaulted());
        debtSim.setInformal(entity.getIsInformal() != null ? entity.getIsInformal() : false);

        return debtSim;
    }

    private BigDecimal normalizeRate(BigDecimal rate) {

        if (rate == null) {
            return BigDecimal.ZERO;
        }

        // ถ้าเก็บ 10 = 10% → แปลงเป็น 0.10
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            return rate.divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
        }

        return rate;
    }
}