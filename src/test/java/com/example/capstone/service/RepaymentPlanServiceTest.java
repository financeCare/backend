package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.entity.*;
import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.factory.StrategyFactory;
import com.example.capstone.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RepaymentPlanServiceTest {

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
    @Mock
    private StrategyFactory strategyFactory;

    @InjectMocks
    private RepaymentPlanService repaymentPlanService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testCreateRepaymentStrategy_Success() {
        RepaymentStrategyDTO dto = new RepaymentStrategyDTO();
        dto.setStrategyName("Snowball");
        dto.setDescription("Pay smallest debt first");

        RepaymentStrategy result = repaymentPlanService.createRepaymentStrategy(dto);

        assertNotNull(result);
        assertEquals("Snowball", result.getStrategyName());
        verify(repaymentStrategyRepository).save(any(RepaymentStrategy.class));
    }

    @Test
    void testGetAllRepaymentStrategies() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(new ArrayList<>());
        when(repaymentStrategyRepository.findAll()).thenReturn(List.of(new RepaymentStrategy()));

        RepaymentStrategyDtoResponse result = repaymentPlanService.getAllRepaymentStrategies(token);

        assertNotNull(result);
        assertNotNull(result.getRepaymentStrategyList());
    }

    @Test
    void testChangeRepaymentPlan_Update() {
        BigDecimal budget = new BigDecimal("5000");
        UUID strategyId = UUID.randomUUID();
        RepaymentPlan existingPlan = new RepaymentPlan();
        existingPlan.setUserId(userId);

        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(existingPlan);
        when(repaymentPlanRepository.save(any())).thenReturn(existingPlan);

        RepaymentPlan result = repaymentPlanService.changeRepaymentPlan(userId, budget, strategyId);

        assertEquals(budget, result.getMonthlyBudget());
        assertEquals(strategyId, result.getStrategyId());
    }

    @Test
    void testSimulate_PlanNotFound() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            repaymentPlanService.simulate(token);
        });
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
    @Test
    void testSimulate_NoDebts() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(repaymentPlanRepository.findByUserId(userId)).thenReturn(new RepaymentPlan());
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(new ArrayList<>());

        PlanResultDTO result = repaymentPlanService.simulate(token);

        assertEquals(0, result.getEstimatedMonths());
        assertEquals(BigDecimal.ZERO, result.getTotalInterest());
    }

    @Test
    void testGetTotalMinPayment_SafeCalculation() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        
        Debt debt1 = new Debt();
        debt1.setPrincipalAmount(new BigDecimal("1000000"));
        debt1.setPrincipalOutstanding(new BigDecimal("1000000"));
        debt1.setInterestRate(new BigDecimal("12")); // 12% annual = 1.0% monthly
        debt1.setInterestCalculationType(InterestCalculationType.THIRTY_360);
        debt1.setMinPayment(new BigDecimal("5000")); // User set only 5000
        debt1.setActive(true);
        
        when(debtRepository.findByActiveAndUserId(true, userId)).thenReturn(List.of(debt1));
        
        BigDecimal result = repaymentPlanService.getTotalMinPayment(token);
        
        // Expected Logic:
        // Interest = 1,000,000 * (12/100/12) = 10,000
        // 1% Principal = 1,000,000 * 0.01 = 10,000
        // SafeMin = 10,000 + 10,000 = 20,000
        // Result = max(5000, 20000) = 20000
        
        assertEquals(new BigDecimal("20000.00"), result);
    }
}
