package com.example.capstone.service;

import com.example.capstone.allocation.DefaultBudgetAllocator;
import com.example.capstone.dto.MonthlyStatusDTO;
import com.example.capstone.dto.PlanResultDTO;
import com.example.capstone.dto.RepaymentStrategyDtoResponse;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.RepaymentPlan;
import com.example.capstone.entity.RepaymentStrategy;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.factory.StrategyFactory;
import com.example.capstone.observability.CacheMetrics;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import com.example.capstone.repository.RepaymentPlanRepository;
import com.example.capstone.repository.RepaymentStrategyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plan changes, strategy ordering, allocation of the monthly budget, the monthly status and its Redis cache. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepaymentPlanServiceBehaviourTest {

    @Mock
    private RepaymentPlanRepository repaymentPlanRepository;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private RepaymentStrategyRepository repaymentStrategyRepository;
    @Mock
    private UserService userService;
    @Mock
    private RepaymentPlanSimulator repaymentPlanSimulator;
    @Spy
    private StrategyFactory strategyFactory = new StrategyFactory();
    @Mock
    private DebtTransactionRepository debtTransactionRepository;
    @Spy
    private DefaultBudgetAllocator allocator = new DefaultBudgetAllocator();
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private CacheMetrics cacheMetrics;
    @Mock
    private PlanCacheEvictor planCacheEvictor;

    @InjectMocks
    private RepaymentPlanService service;

    private UUID userId;
    private final String token = "token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private Debt debt(String name, String outstanding, String rate, String minPayment, boolean informal) {
        Debt d = new Debt();
        d.setDebtId(UUID.randomUUID());
        d.setUserId(userId);
        d.setDebtName(name);
        d.setPrincipalAmount(new BigDecimal(outstanding));
        d.setPrincipalOutstanding(new BigDecimal(outstanding));
        d.setInterestRate(new BigDecimal(rate));
        d.setMinPayment(new BigDecimal(minPayment));
        d.setIsInformal(informal);
        d.setActive(true);
        d.setDefaulted(false);
        d.setDueDay(15);
        d.setGracePeriodDays(0);
        d.setPenaltyTriggerDays(0);
        RepaymentType emi = new RepaymentType();
        emi.setRepaymentTypeName("EMI");
        d.setRepaymentType(emi);
        d.setStartDate(new Date());
        return d;
    }

    private RepaymentStrategy strategy(String name) {
        RepaymentStrategy s = new RepaymentStrategy();
        s.setStrategyId(UUID.randomUUID());
        s.setStrategyName(name);
        when(repaymentStrategyRepository.findById(s.getStrategyId())).thenReturn(Optional.of(s));
        return s;
    }

    private RepaymentPlan plan(String budget, UUID strategyId) {
        RepaymentPlan p = new RepaymentPlan();
        p.setPlanId(UUID.randomUUID());
        p.setUserId(userId);
        p.setMonthlyBudget(budget == null ? null : new BigDecimal(budget));
        p.setStrategyId(strategyId);
        return p;
    }

    // ---------- changing the plan ----------

    @Test
    void changePlan_createsAPlanWhenTheUserHasNone_andRanksTheDebtsBySnowball() {
        Debt big = debt("big", "9000", "0.10", "300", false);
        Debt small = debt("small", "1000", "0.30", "100", false);
        Debt shark = debt("shark", "5000", "0.60", "200", true);
        RepaymentStrategy snowball = strategy("Snowball");
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        when(repaymentPlanRepository.save(any(RepaymentPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(big, small, shark));

        RepaymentPlan saved = service.changeRepaymentPlan(userId, new BigDecimal("2000"), snowball.getStrategyId());

        assertEquals(new BigDecimal("2000"), saved.getMonthlyBudget());
        assertEquals(snowball.getStrategyId(), saved.getStrategyId());
        // informal debts first, then the smallest balance
        assertEquals(1, shark.getPriority());
        assertEquals(2, small.getPriority());
        assertEquals(3, big.getPriority());
        verify(debtRepository).saveAll(any());
        verify(planCacheEvictor).evictUserCache(userId);
    }

    @Test
    void changePlan_updatesTheExistingPlan_andRanksTheDebtsByAvalanche() {
        Debt lowRate = debt("low", "1000", "0.05", "100", false);
        Debt highRate = debt("high", "5000", "0.25", "100", false);
        RepaymentStrategy avalanche = strategy("Avalanche Strategy");
        RepaymentPlan existing = plan("1000", UUID.randomUUID());
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(existing);
        when(repaymentPlanRepository.save(any(RepaymentPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(lowRate, highRate));

        RepaymentPlan saved = service.changeRepaymentPlan(userId, new BigDecimal("3000"), avalanche.getStrategyId());

        assertSame(existing, saved);
        assertEquals(new BigDecimal("3000"), existing.getMonthlyBudget());
        assertEquals(1, highRate.getPriority());
        assertEquals(2, lowRate.getPriority());
    }

    @Test
    void changePlan_withoutDebts_doesNotLookUpTheStrategy() {
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        when(repaymentPlanRepository.save(any(RepaymentPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of());

        service.changeRepaymentPlan(userId, new BigDecimal("100"), UUID.randomUUID());

        verify(repaymentStrategyRepository, never()).findById(any());
        verify(debtRepository, never()).saveAll(any());
    }

    @Test
    void changePlan_unknownStrategyId_isNotFound() {
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        when(repaymentPlanRepository.save(any(RepaymentPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(debt("a", "1", "0.1", "1", false)));
        when(repaymentStrategyRepository.findById(any())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeRepaymentPlan(userId, BigDecimal.TEN, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void changePlan_strategyWithAnUnknownName_isABadRequest() {
        RepaymentStrategy weird = strategy("Made Up");
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        when(repaymentPlanRepository.save(any(RepaymentPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(debt("a", "1", "0.1", "1", false)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeRepaymentPlan(userId, BigDecimal.TEN, weird.getStrategyId()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ---------- priority suggestions ----------

    @Test
    void prioritySuggestions_listTheDebtsInStrategyOrder_withCurrentAndSuggestedPriority() {
        Debt a = debt("a", "9000", "0.10", "100", false);
        a.setPriority(1);
        Debt b = debt("b", "1000", "0.10", "100", false);
        RepaymentStrategy snowball = strategy("SNOWBALL");
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(a, b));

        List<Map<String, Object>> result = service.getPrioritySuggestions(token, snowball.getStrategyId());

        assertEquals("b", result.get(0).get("debtName"));
        assertEquals(1, result.get(0).get("suggestedPriority"));
        assertEquals(0, result.get(0).get("currentPriority"));    // no priority yet
        assertEquals("a", result.get(1).get("debtName"));
        assertEquals(1, result.get(1).get("currentPriority"));
        assertEquals(2, result.get(1).get("suggestedPriority"));
    }

    @Test
    void prioritySuggestions_unknownStrategy_isNotFound() {
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of());
        when(repaymentStrategyRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getPrioritySuggestions(token, UUID.randomUUID()));
    }

    // ---------- minimum payments ----------

    @Test
    void safeMinimumPayment_isInterestPlusOnePercent_unlessTheDebtsOwnMinimumIsHigher() {
        Debt low = debt("low", "100000", "12", "1", false);       // 12% a year, user minimum far too low
        Debt high = debt("high", "100000", "12", "50000", false); // user minimum above the safe figure

        // 12% of 100,000 per month interest (1,000) + 1% principal (1,000), rounded up to whole units
        assertEquals(0, new BigDecimal("2000").compareTo(service.calculateSafeMinPayment(low)));
        assertEquals(0, new BigDecimal("50000").compareTo(service.calculateSafeMinPayment(high)));
    }

    @Test
    void safeMinimumPayment_isZeroForAPaidOffDebt() {
        Debt paid = debt("paid", "0", "10", "500", false);
        Debt noBalance = debt("none", "100", "10", "500", false);
        noBalance.setPrincipalOutstanding(null);
        noBalance.setPrincipalAmount(null);

        assertEquals(BigDecimal.ZERO, service.calculateSafeMinPayment(paid));
        assertEquals(BigDecimal.ZERO, service.calculateSafeMinPayment(noBalance));
    }

    @Test
    void totalMinPayment_sumsTheSafeMinimumOfEveryActiveDebt() {
        Debt a = debt("a", "100000", "12", "1", false);
        Debt b = debt("b", "100000", "12", "1", false);
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(a, b));

        assertEquals(0, new BigDecimal("4000").compareTo(service.getTotalMinPayment(token)));
    }

    // ---------- allocation of the monthly budget ----------

    @Test
    void allocation_isEmptyWithoutAPlanABudgetOrDebts() {
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        assertTrue(service.calculateCurrentMonthAllocation(userId).isEmpty());

        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("0", UUID.randomUUID()));
        assertTrue(service.calculateCurrentMonthAllocation(userId).isEmpty());

        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("1000", UUID.randomUUID()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of());
        assertTrue(service.calculateCurrentMonthAllocation(userId).isEmpty());
    }

    @Test
    void allocation_paysEveryMinimumAndGivesTheRestToTheStrategyTarget() {
        Debt small = debt("small", "1000", "0.10", "100", false);
        Debt big = debt("big", "9000", "0.10", "300", false);
        RepaymentStrategy snowball = strategy("SNOWBALL");
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("1000", snowball.getStrategyId()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(big, small));

        Map<UUID, BigDecimal> allocation = service.calculateCurrentMonthAllocation(userId);

        assertEquals(0, new BigDecimal("300").compareTo(allocation.get(big.getDebtId())));
        assertEquals(0, new BigDecimal("700").compareTo(allocation.get(small.getDebtId())));   // 100 minimum + 600 extra
    }

    @Test
    void allocation_ratesAreNormalised_percentValuesAndDailyIntervals() {
        Debt percent = debt("percent", "1000", "24", "100", false);     // "24" means 24%
        Debt daily = debt("daily", "1000", "0.0005", "100", false);
        daily.setInterestRate(new BigDecimal("0.0005"));
        daily.setInterestInterval(InterestInterval.DAILY);
        Debt monthly = debt("monthly", "1000", "0.02", "100", false);
        monthly.setInterestInterval(InterestInterval.MONTHLY);
        RepaymentStrategy avalanche = strategy("AVALANCHE");
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("600", avalanche.getStrategyId()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(percent, daily, monthly));

        Map<UUID, BigDecimal> allocation = service.calculateCurrentMonthAllocation(userId);

        // all three have an annual rate of about 24%, so every minimum is paid and the extra goes somewhere
        BigDecimal total = allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("600").compareTo(total));
        assertEquals(3, allocation.size());
    }

    @Test
    void allocation_debtWithNullDayCountsFromBeforeThoseColumnsExisted_doesNotCrash() {
        Debt legacy = debt("legacy", "1000", "0.10", "100", false);
        legacy.setGracePeriodDays(null);
        legacy.setPenaltyTriggerDays(null);
        legacy.setDueDay(null);
        legacy.setDefaulted(null);
        RepaymentStrategy snowball = strategy("SNOWBALL");
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("500", snowball.getStrategyId()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(legacy));

        Map<UUID, BigDecimal> allocation = service.calculateCurrentMonthAllocation(userId);

        assertEquals(0, new BigDecimal("500").compareTo(allocation.get(legacy.getDebtId())));
    }

    // ---------- simulation ----------

    @Test
    void simulate_withoutAPlan_isNotFound() {
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.simulate(token));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void simulate_withoutDebts_returnsAnEmptyResultWithoutRunningTheSimulator() {
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("1000", UUID.randomUUID()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of());

        PlanResultDTO result = service.simulate(token);

        assertEquals(0, result.getEstimatedMonths());
        verifyNoInteractions(repaymentPlanSimulator);
    }

    @Test
    void simulate_runsTheSimulatorWithTheUsersPlanAndDebts() {
        RepaymentStrategy snowball = strategy("SNOWBALL");
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("1000", snowball.getStrategyId()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(debt("a", "1000", "0.1", "100", false)));
        PlanResultDTO expected = new PlanResultDTO(7, BigDecimal.TEN, BigDecimal.ONE, List.of());
        when(repaymentPlanSimulator.simulateCore(any(), any(), any())).thenReturn(expected);

        assertSame(expected, service.simulate(token));
    }

    @Test
    void simulate_planWithoutAStrategy_isRejected() {
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("1000", null));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(debt("a", "1000", "0.1", "100", false)));

        assertThrows(IllegalStateException.class, () -> service.simulate(token));
    }

    // ---------- monthly status and its cache ----------

    @Test
    void monthlyStatus_cacheHit_skipsTheDatabase() throws Exception {
        MonthlyStatusDTO cached = new MonthlyStatusDTO(new BigDecimal("500"), new BigDecimal("100"), new BigDecimal("400"),
                new BigDecimal("50"), new BigDecimal("60"), false);
        when(valueOps.get("plans:monthly-status:" + userId)).thenReturn(new ObjectMapper().writeValueAsString(cached));

        MonthlyStatusDTO result = service.getMonthlyStatus(token);

        assertEquals(0, new BigDecimal("500").compareTo(result.getTotalAmount()));
        verify(cacheMetrics).hit("monthly_status");
        verifyNoInteractions(debtRepository, repaymentPlanRepository);
    }

    @Test
    void monthlyStatus_cacheMiss_usesThePlanBudgetAndStoresTheResultForFiveMinutes() {
        Debt a = debt("a", "100000", "12", "1000", false);
        when(valueOps.get(anyString())).thenReturn(null);
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("9000", UUID.randomUUID()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(a));
        when(debtTransactionRepository.sumPaymentsByUserIdAndMonth(eq(userId), anyInt(), anyInt())).thenReturn(new BigDecimal("2500"));

        MonthlyStatusDTO status = service.getMonthlyStatus(token);

        assertEquals(0, new BigDecimal("9000").compareTo(status.getTotalAmount()));
        assertEquals(0, new BigDecimal("2500").compareTo(status.getPaidAmount()));
        assertEquals(0, new BigDecimal("6500").compareTo(status.getRemainingAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(status.getActualMinPayment()));
        assertEquals(0, new BigDecimal("2000").compareTo(status.getRequiredMinPayment()));
        assertFalse(status.isBudgetInsufficient());
        verify(cacheMetrics).miss("monthly_status");
        verify(valueOps).set(eq("plans:monthly-status:" + userId), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void monthlyStatus_withoutAPlan_fallsBackToTheSumOfMinimums_andRemainingNeverGoesNegative() {
        Debt a = debt("a", "100000", "12", "1000", false);
        Debt b = debt("b", "100000", "12", "500", false);
        when(valueOps.get(anyString())).thenReturn(null);
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(a, b));
        when(debtTransactionRepository.sumPaymentsByUserIdAndMonth(eq(userId), anyInt(), anyInt())).thenReturn(new BigDecimal("5000"));

        MonthlyStatusDTO status = service.getMonthlyStatus(token);

        assertEquals(0, new BigDecimal("1500").compareTo(status.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(status.getRemainingAmount()));   // paid more than due
        assertFalse(status.isBudgetInsufficient());                                  // no plan, nothing to compare
    }

    @Test
    void monthlyStatus_budgetBelowTheSafeMinimum_isFlagged() {
        Debt a = debt("a", "100000", "12", "1", false);
        when(valueOps.get(anyString())).thenReturn(null);
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("500", UUID.randomUUID()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(a));
        when(debtTransactionRepository.sumPaymentsByUserIdAndMonth(eq(userId), anyInt(), anyInt())).thenReturn(null);   // nothing paid

        MonthlyStatusDTO status = service.getMonthlyStatus(token);

        assertTrue(status.isBudgetInsufficient());
        assertEquals(0, BigDecimal.ZERO.compareTo(status.getPaidAmount()));
    }

    @Test
    void monthlyStatus_noActiveDebts_isAllZero() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(plan("1000", UUID.randomUUID()));
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of());
        when(debtTransactionRepository.sumPaymentsByUserIdAndMonth(eq(userId), anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);

        MonthlyStatusDTO status = service.getMonthlyStatus(token);

        assertEquals(0, BigDecimal.ZERO.compareTo(status.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(status.getRequiredMinPayment()));
    }

    @Test
    void monthlyStatus_redisFailure_fallsBackToTheDatabase() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of());
        when(debtTransactionRepository.sumPaymentsByUserIdAndMonth(eq(userId), anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);

        assertNotNull(service.getMonthlyStatus(token));
    }

    // ---------- strategies summary and its cache ----------

    @Test
    void strategies_cacheMiss_sumsTheMinimumsAndCachesForSevenDays() {
        Debt a = debt("a", "100000", "12", "1000", false);
        RepaymentStrategy snowball = strategy("SNOWBALL");
        when(valueOps.get(anyString())).thenReturn(null);
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(a));
        when(repaymentStrategyRepository.findAll()).thenReturn(List.of(snowball));

        RepaymentStrategyDtoResponse response = service.getAllRepaymentStrategies(token);

        assertEquals(1000.0, response.getActualMinSum());
        assertEquals(2000.0, response.getSafeMinSum());
        assertEquals(1, response.getRepaymentStrategyList().size());
        verify(cacheMetrics).miss("plan_strategies");
        verify(valueOps).set(eq("plans:strategies:" + userId), anyString(), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void strategies_cacheHit_returnsTheCachedSummary() throws Exception {
        RepaymentStrategyDtoResponse cached = new RepaymentStrategyDtoResponse(11.0, 22.0, List.of());
        when(valueOps.get("plans:strategies:" + userId)).thenReturn(new ObjectMapper().writeValueAsString(cached));

        RepaymentStrategyDtoResponse response = service.getAllRepaymentStrategies(token);

        assertEquals(22.0, response.getSafeMinSum());
        verify(cacheMetrics).hit("plan_strategies");
        verifyNoInteractions(debtRepository);
    }

    @Test
    void evictUserCache_delegatesToTheEvictor() {
        service.evictUserCache(userId);

        verify(planCacheEvictor).evictUserCache(userId);
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(planCacheEvictor).evictUserCache(id.capture());
        assertEquals(userId, id.getValue());
    }
}
