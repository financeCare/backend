package com.example.capstone.service;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.dto.*;
import com.example.capstone.enums.RepaymentTypeEnum;
import com.example.capstone.entity.*;
import com.example.capstone.entity.RepaymentPlan;
import com.example.capstone.engine.calculator.InterestCalculator;
import com.example.capstone.enums.StrategyType;
import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.factory.StrategyFactory;
import com.example.capstone.repository.*;
import com.example.capstone.strategy.repayment.RepaymentStrategyInterface;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@AllArgsConstructor
public class RepaymentPlanService {
    private static final String STRATEGY_NOT_FOUND_MSG = "Strategy not found";
    private static final String STRATEGY_NOT_FOUND_CODE = "STRATEGY_NOT_FOUND";
    private static final String UNKNOWN_STRATEGY_TYPE_MSG = "Unknown strategy type";
    private static final String UNKNOWN_STRATEGY_TYPE_CODE = "UNKNOWN_STRATEGY_TYPE";
    private static final String STRATEGIES_CACHE_PREFIX = "plans:strategies:";
    private static final String MONTHLY_STATUS_CACHE_PREFIX = "plans:monthly-status:";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Bangkok");

    private final RepaymentPlanRepository repaymentPlanRepository;
    private final DebtRepository debtRepository;
    private final RepaymentStrategyRepository repaymentStrategyRepository;
    private final UserService userService;
    private final RepaymentPlanSimulator repaymentPlanSimulator;
    private final StrategyFactory strategyFactory;
    private final DebtTransactionRepository debtTransactionRepository;
    private final com.example.capstone.allocation.DefaultBudgetAllocator allocator;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RepaymentStrategy createRepaymentStrategy(RepaymentStrategyDTO repaymentStrategyDTO) {
        RepaymentStrategy strategy = new RepaymentStrategy();
        strategy.setStrategyName(repaymentStrategyDTO.getStrategyName());
        strategy.setDescription(repaymentStrategyDTO.getDescription());
        strategy.setIsActive(true);
        try {
            repaymentStrategyRepository.save(strategy);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage(), "STRATEGY_LOAD_FAILED", HttpStatus.NOT_FOUND);
        }
        return strategy;
    }

    public RepaymentStrategyDtoResponse getAllRepaymentStrategies(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        String cacheKey = STRATEGIES_CACHE_PREFIX + userId;
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                return objectMapper.readValue(cachedData, RepaymentStrategyDtoResponse.class);
            }
        } catch (Exception e) {
            // Fallback
        }

        List<Debt> debts = debtRepository.findByActiveAndUserId(true, userId);
        
        BigDecimal actualMinSum = debts.stream()
                .map(d -> d.getMinPayment() != null ? d.getMinPayment() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal safeMinSum = debts.stream()
                .map(this::calculateSafeMinPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        RepaymentStrategyDtoResponse response = new RepaymentStrategyDtoResponse(actualMinSum.doubleValue(), safeMinSum.doubleValue(), repaymentStrategyRepository.findAll());
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonData, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            // Fallback
        }
        return response;
    }

    public String deleteRepaymentStrategy(UUID strategyId) {
        repaymentStrategyRepository.findById(strategyId).ifPresent(repaymentStrategyRepository::delete);
        return "Repayment Strategy id " + strategyId + " delete successfully";
    }

    public RepaymentPlan changeRepaymentPlan(UUID userId, BigDecimal monthlyBudget, UUID strategyId) {
        RepaymentPlan repaymentPlan = repaymentPlanRepository.findByUserId(userId);
        RepaymentPlan savedPlan;
        if (repaymentPlan != null) {
            repaymentPlan.setMonthlyBudget(monthlyBudget);
            repaymentPlan.setStrategyId(strategyId);
            savedPlan = repaymentPlanRepository.save(repaymentPlan);
        } else {
            RepaymentPlan plan = new RepaymentPlan();
            plan.setPlanId(UUID.randomUUID());
            plan.setUserId(userId);
            plan.setMonthlyBudget(monthlyBudget);
            plan.setStrategyId(strategyId);
            savedPlan = repaymentPlanRepository.save(plan);
        }
        applyStrategyPriority(userId, strategyId);
        evictUserCache(userId);
        return savedPlan;
    }

    private void applyStrategyPriority(UUID userId, UUID strategyId) {
        List<Debt> debts = debtRepository.findByActiveAndUserId(true, userId);
        if (debts.isEmpty()) {
            return;
        }

        RepaymentStrategy strategyEntity = repaymentStrategyRepository.findById(strategyId)
                .orElseThrow(() -> new BusinessException(STRATEGY_NOT_FOUND_MSG, STRATEGY_NOT_FOUND_CODE, HttpStatus.NOT_FOUND));

        StrategyType type;
        try {
            type = StrategyType.fromString(strategyEntity.getStrategyName());
        } catch (Exception e) {
            throw new BusinessException(UNKNOWN_STRATEGY_TYPE_MSG, UNKNOWN_STRATEGY_TYPE_CODE, HttpStatus.BAD_REQUEST);
        }

        List<Debt> sortedDebts = new ArrayList<>(debts);
        sortedDebts.sort(getStrategyComparator(type));

        for (int i = 0; i < sortedDebts.size(); i++) {
            sortedDebts.get(i).setPriority(i + 1);
        }
        debtRepository.saveAll(sortedDebts);
    }

    private Comparator<Debt> getStrategyComparator(StrategyType type) {
        Comparator<Debt> comparator = Comparator.comparing(
                (Debt d) -> Boolean.TRUE.equals(d.getIsInformal()),
                Comparator.reverseOrder()
        );

        if (type == StrategyType.SNOWBALL) {
            return comparator
                    .thenComparing(Debt::getPrincipalOutstanding, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Debt::getPrincipalAmount, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if (type == StrategyType.AVALANCHE || type == StrategyType.OPTIMAL_COST) {
            return comparator
                    .thenComparing(Debt::getInterestRate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return comparator;
    }

    public BigDecimal getTotalMinPayment(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Debt> debts = debtRepository.findByActiveAndUserId(true, userId);
        return debts.stream()
                .map(this::calculateSafeMinPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateSafeMinPayment(Debt debt) {
        BigDecimal principal = debt.getPrincipalOutstanding() != null ? debt.getPrincipalOutstanding() : debt.getPrincipalAmount();
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal annualRate = normalizeRate(debt.getInterestRate());
        BigDecimal monthlyInterest = InterestCalculator.calculate(
                principal,
                annualRate,
                debt.getInterestCalculationType() != null ? debt.getInterestCalculationType() : com.example.capstone.enums.InterestCalculationType.THIRTY_360
        );

        // Safe min = Interest + 1% of Principal to ensure principal decreases
        BigDecimal onePercentPrincipal = principal.multiply(BigDecimal.valueOf(0.01)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal safeMin = monthlyInterest.add(onePercentPrincipal);
        
        // If 1% principal is too small, ensure at least 1 unit payment
        if (safeMin.compareTo(monthlyInterest) <= 0) {
            safeMin = monthlyInterest.add(BigDecimal.ONE);
        }

        if (debt.getMinPayment() != null && debt.getMinPayment().compareTo(safeMin) > 0) {
            return debt.getMinPayment();
        }
        return safeMin.setScale(0, RoundingMode.CEILING);
    }

    @Transactional(readOnly = true)
    public PlanResultDTO simulate(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        RepaymentPlan planEntity = repaymentPlanRepository.findByUserId(userId);

        if (planEntity == null) {
            throw new BusinessException("Repayment plan not found", "PLAN_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        List<Debt> debtEntities = debtRepository.findByActiveAndUserId(true, userId);

        if (debtEntities.isEmpty()) {
            return new PlanResultDTO(0, BigDecimal.ZERO, BigDecimal.ZERO, new ArrayList<>());
        }

        RepaymentPlanDtoV2 simPlan = mapToDomainPlan(planEntity);
        List<DebtSim> simDebts = debtEntities.stream().map(this::mapToDomainDebt).toList();
        RepaymentStrategyInterface strategy = strategyFactory.getStrategy(simPlan.getStrategyType());
        return repaymentPlanSimulator.simulateCore(simPlan, simDebts, strategy);
    }

    private RepaymentPlanDtoV2 mapToDomainPlan(RepaymentPlan entity) {
        if (entity == null) throw new IllegalArgumentException("RepaymentPlanEntity must not be null");
        if (entity.getStrategyId() == null) throw new IllegalStateException("Strategy is not configured for this plan");

        RepaymentPlanDtoV2 plan = new RepaymentPlanDtoV2();
        plan.setMonthlyBudget(entity.getMonthlyBudget() != null ? entity.getMonthlyBudget() : BigDecimal.ZERO);

        String strategyName = repaymentStrategyRepository.findById(entity.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException(STRATEGY_NOT_FOUND_MSG)).getStrategyName();

        try {
            plan.setStrategyType(StrategyType.fromString(strategyName));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown strategy type in database: " + strategyName);
        }

        return plan;
    }

    private DebtSim mapToDomainDebt(Debt entity) {
        if (entity == null) throw new IllegalArgumentException("Debt entity must not be null");

        DebtSim debtSim = new DebtSim();
        debtSim.setDebtId(entity.getDebtId());
        debtSim.setDebtName(entity.getDebtName());
        debtSim.setPrincipal(entity.getPrincipalOutstanding() != null ? entity.getPrincipalOutstanding() : entity.getPrincipalAmount());
        debtSim.setAnnualInterestRate(normalizeAnnualRate(entity.getInterestRate(), entity.getInterestInterval()));
        debtSim.setInterestType(entity.getInterestCalculationType());
        debtSim.setMinPayment(entity.getMinPayment() != null ? entity.getMinPayment() : BigDecimal.ZERO);
        debtSim.setActive(entity.getActive());
        
        // Calculate outstanding amounts directly to avoid circular dependency with DebtService
        UUID debtId = entity.getDebtId();
        BigDecimal interest = debtTransactionRepository.sumOutstandingByType(debtId, DebtTxnType.INTEREST_CHARGE);
        BigDecimal lateFee = debtTransactionRepository.sumOutstandingByType(debtId, DebtTxnType.LATE_FEE_CHARGE);
        BigDecimal penalty = debtTransactionRepository.sumOutstandingByType(debtId, DebtTxnType.PENALTY_INTEREST_CHARGE);
        
        debtSim.setInterestOutstanding(interest != null ? interest : BigDecimal.ZERO);
        debtSim.setLateFeeOutstanding(lateFee != null ? lateFee : BigDecimal.ZERO);
        debtSim.setPenaltyOutstanding(penalty != null ? penalty : BigDecimal.ZERO);

        debtSim.setRepaymentType(RepaymentTypeEnum.fromString(entity.getRepaymentType().getRepaymentTypeName()));

        if (entity.getStartDate() != null) {
            LocalDate localStartDate = entity.getStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            debtSim.setStartDate(localStartDate);
            debtSim.setCurrentDate(localStartDate);
        }
        
        if (entity.getEndDate() != null) {
            LocalDate localEndDate = entity.getEndDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            debtSim.setEndDate(localEndDate);
        }

        debtSim.setOriginalPrincipal(entity.getPrincipalAmount());
        debtSim.setDueDay(entity.getDueDay());
        debtSim.setGracePeriodDays(entity.getGracePeriodDays());
        debtSim.setPenaltyTriggerDays(entity.getPenaltyTriggerDays());
        debtSim.setPriority(entity.getPriority() != null ? entity.getPriority() : 999);
        
        debtSim.setPenaltyAnnualRate(normalizeAnnualRate(entity.getPenaltyAnnualRate(), InterestInterval.YEARLY));
        debtSim.setDefaulted(entity.getDefaulted());
        debtSim.setInformal(Boolean.TRUE.equals(entity.getIsInformal()));
        
        return debtSim;
    }

    private BigDecimal normalizeAnnualRate(BigDecimal rate, InterestInterval interval) {
        if (rate == null) return BigDecimal.ZERO;
        
        BigDecimal normalizedRate = rate;
        if (normalizedRate.compareTo(BigDecimal.ONE) > 0) {
            normalizedRate = normalizedRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        }
        
        if (interval == null) interval = InterestInterval.YEARLY;
        
        return switch (interval) {
            case DAILY -> normalizedRate.multiply(BigDecimal.valueOf(365));
            case MONTHLY -> normalizedRate.multiply(BigDecimal.valueOf(12));
            case YEARLY -> normalizedRate;
            default -> normalizedRate;
        };
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        return normalizeAnnualRate(rate, InterestInterval.YEARLY);
    }

    public List<Map<String, Object>> getPrioritySuggestions(String token, UUID strategyId) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Debt> debts = debtRepository.findByActiveAndUserId(true, userId);
        
        RepaymentStrategy strategyEntity = repaymentStrategyRepository.findById(strategyId)
                .orElseThrow(() -> new BusinessException(STRATEGY_NOT_FOUND_MSG, STRATEGY_NOT_FOUND_CODE, HttpStatus.NOT_FOUND));
        
        StrategyType type;
        try {
            type = StrategyType.fromString(strategyEntity.getStrategyName());
        } catch (Exception e) {
            throw new BusinessException(UNKNOWN_STRATEGY_TYPE_MSG, UNKNOWN_STRATEGY_TYPE_CODE, HttpStatus.BAD_REQUEST);
        }

        List<Debt> sortedDebts = new ArrayList<>(debts);
        sortedDebts.sort(getStrategyComparator(type));

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (int i = 0; i < sortedDebts.size(); i++) {
            Map<String, Object> suggestion = new HashMap<>();
            suggestion.put("debtId", sortedDebts.get(i).getDebtId());
            suggestion.put("debtName", sortedDebts.get(i).getDebtName());
            suggestion.put("principalOutstanding", sortedDebts.get(i).getPrincipalOutstanding() != null ? sortedDebts.get(i).getPrincipalOutstanding() : sortedDebts.get(i).getPrincipalAmount());
            suggestion.put("interestRate", sortedDebts.get(i).getInterestRate());
            suggestion.put("isInformal", sortedDebts.get(i).getIsInformal());
            suggestion.put("currentPriority", sortedDebts.get(i).getPriority() != null ? sortedDebts.get(i).getPriority() : 0);
            suggestion.put("suggestedPriority", i + 1);
            suggestions.add(suggestion);
        }

        return suggestions;
    }

    public MonthlyStatusDTO getMonthlyStatus(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        String cacheKey = MONTHLY_STATUS_CACHE_PREFIX + userId;
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                return objectMapper.readValue(cachedData, MonthlyStatusDTO.class);
            }
        } catch (Exception e) {
            // Fallback
        }

        LocalDate now = LocalDate.now(DEFAULT_ZONE);
        int year = now.getYear();
        int month = now.getMonthValue();

        RepaymentPlan plan = repaymentPlanRepository.findByUserId(userId);
        List<Debt> activeDebts = debtRepository.findByActiveAndUserId(true, userId);
        BigDecimal totalAmount;

        if (activeDebts.isEmpty()) {
            totalAmount = BigDecimal.ZERO;
        } else if (plan != null && plan.getMonthlyBudget() != null && plan.getMonthlyBudget().compareTo(BigDecimal.ZERO) > 0) {
            totalAmount = plan.getMonthlyBudget();
        } else {
            totalAmount = activeDebts.stream()
                    .map(d -> d.getMinPayment() != null ? d.getMinPayment() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal paidAmount = debtTransactionRepository.sumPaymentsByUserIdAndMonth(userId, year, month);
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;

        BigDecimal actualMinPayment = activeDebts.stream()
                .map(d -> d.getMinPayment() != null ? d.getMinPayment() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal requiredMinPayment = activeDebts.stream()
                .map(this::calculateSafeMinPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isBudgetInsufficient = plan != null && plan.getMonthlyBudget() != null 
                && plan.getMonthlyBudget().compareTo(BigDecimal.ZERO) > 0 
                && plan.getMonthlyBudget().compareTo(requiredMinPayment) < 0;

        BigDecimal remainingAmount = totalAmount.subtract(paidAmount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        MonthlyStatusDTO response = new MonthlyStatusDTO(totalAmount, paidAmount, remainingAmount, actualMinPayment, requiredMinPayment, isBudgetInsufficient);
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonData, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            // Fallback
        }
        return response;
    }

    public Map<UUID, BigDecimal> calculateCurrentMonthAllocation(UUID userId) {
        RepaymentPlan planEntity = repaymentPlanRepository.findByUserId(userId);
        if (planEntity == null || planEntity.getMonthlyBudget() == null || planEntity.getMonthlyBudget().compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyMap();
        }

        List<Debt> debtEntities = debtRepository.findByActiveAndUserId(true, userId);
        if (debtEntities.isEmpty()) {
            return Collections.emptyMap();
        }

        RepaymentPlanDtoV2 simPlan = mapToDomainPlan(planEntity);
        List<DebtSim> simDebts = debtEntities.stream().map(this::mapToDomainDebt).toList();
        RepaymentStrategyInterface strategy = strategyFactory.getStrategy(simPlan.getStrategyType());

        DebtSim target = strategy.apply(simDebts);
        return allocator.allocate(simPlan.getMonthlyBudget(), simDebts, target);
    }

    public void evictUserCache(UUID userId) {
        try {
            redisTemplate.delete(STRATEGIES_CACHE_PREFIX + userId);
            redisTemplate.delete(MONTHLY_STATUS_CACHE_PREFIX + userId);
        } catch (Exception e) {
            // Fallback
        }
    }
}