package com.example.capstone.controller;

import com.example.capstone.dto.RepaymentPlanDTO;
import com.example.capstone.dto.RepaymentStrategyDTO;
import com.example.capstone.dto.RepaymentStrategyDtoResponse;
import com.example.capstone.entity.RepaymentPlan;
import com.example.capstone.entity.RepaymentStrategy;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.RepaymentPlanService;
import com.example.capstone.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepaymentPlanController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class RepaymentPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepaymentPlanService repaymentPlanService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── POST /repayment-plans/strategies ───────────────────────────────────────

    @Test
    void testCreateRepaymentStrategy_Success() throws Exception {
        RepaymentStrategyDTO dto = new RepaymentStrategyDTO();
        dto.setStrategyName("AVALANCHE");
        dto.setDescription("Pay highest interest debt first");

        RepaymentStrategy strategy = new RepaymentStrategy();
        strategy.setStrategyId(UUID.randomUUID());
        strategy.setStrategyName("AVALANCHE");
        strategy.setDescription("Pay highest interest debt first");
        strategy.setIsActive(true);

        when(repaymentPlanService.createRepaymentStrategy(any(RepaymentStrategyDTO.class)))
                .thenReturn(strategy);

        mockMvc.perform(post("/repayment-plans/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyName").value("AVALANCHE"))
                .andExpect(jsonPath("$.description").value("Pay highest interest debt first"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(repaymentPlanService, times(1))
                .createRepaymentStrategy(any(RepaymentStrategyDTO.class));
    }

    @Test
    void testCreateRepaymentStrategy_ValidationFail_BlankName() throws Exception {
        RepaymentStrategyDTO dto = new RepaymentStrategyDTO();
        dto.setStrategyName("");          // ข้อมูลไม่ถูกต้อง
        dto.setDescription("Some description");

        mockMvc.perform(post("/repayment-plans/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(repaymentPlanService, never())
                .createRepaymentStrategy(any(RepaymentStrategyDTO.class));
    }

    @Test
    void testCreateRepaymentStrategy_ValidationFail_BlankDescription() throws Exception {
        RepaymentStrategyDTO dto = new RepaymentStrategyDTO();
        dto.setStrategyName("SNOWBALL");
        dto.setDescription("");           // ข้อมูลไม่ถูกต้อง

        mockMvc.perform(post("/repayment-plans/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(repaymentPlanService, never())
                .createRepaymentStrategy(any(RepaymentStrategyDTO.class));
    }

    // ─── GET /repayment-plans/strategies ────────────────────────────────────────

    @Test
    void testGetAllRepaymentStrategies_Success() throws Exception {
        RepaymentStrategy s1 = new RepaymentStrategy();
        s1.setStrategyId(UUID.randomUUID());
        s1.setStrategyName("AVALANCHE");

        RepaymentStrategy s2 = new RepaymentStrategy();
        s2.setStrategyId(UUID.randomUUID());
        s2.setStrategyName("SNOWBALL");

        List<RepaymentStrategy> strategies = Arrays.asList(s1, s2);
        RepaymentStrategyDtoResponse response = new RepaymentStrategyDtoResponse(5000.0, 5000.0, strategies);

        when(repaymentPlanService.getAllRepaymentStrategies(anyString())).thenReturn(response);

        mockMvc.perform(get("/repayment-plans/strategies")
                        .header("Authorization", "Bearer test-token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualMinSum").value(5000.0))
                .andExpect(jsonPath("$.safeMinSum").value(5000.0))
                .andExpect(jsonPath("$.repaymentStrategyList").isArray())
                .andExpect(jsonPath("$.repaymentStrategyList.length()").value(2))
                .andExpect(jsonPath("$.repaymentStrategyList[0].strategyName").value("AVALANCHE"))
                .andExpect(jsonPath("$.repaymentStrategyList[1].strategyName").value("SNOWBALL"));

        verify(repaymentPlanService, times(1)).getAllRepaymentStrategies(anyString());
    }

    @Test
    void testGetAllRepaymentStrategies_EmptyList() throws Exception {
        RepaymentStrategyDtoResponse response = new RepaymentStrategyDtoResponse(0.0, 0.0, List.of());

        when(repaymentPlanService.getAllRepaymentStrategies(anyString())).thenReturn(response);

        mockMvc.perform(get("/repayment-plans/strategies")
                        .header("Authorization", "Bearer test-token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualMinSum").value(0.0))
                .andExpect(jsonPath("$.safeMinSum").value(0.0))
                .andExpect(jsonPath("$.repaymentStrategyList").isArray())
                .andExpect(jsonPath("$.repaymentStrategyList.length()").value(0));
    }

    // ─── DELETE /repayment-plans/strategies/{id} ────────────────────────────────

    @Test
    void testDeleteRepaymentStrategy_Success() throws Exception {
        UUID strategyId = UUID.randomUUID();
        String expectedMsg = "Repayment Strategy id " + strategyId + " delete successfully";

        when(repaymentPlanService.deleteRepaymentStrategy(any(UUID.class)))
                .thenReturn(expectedMsg);

        mockMvc.perform(delete("/repayment-plans/strategies/" + strategyId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(expectedMsg));

        verify(repaymentPlanService, times(1)).deleteRepaymentStrategy(eq(strategyId));
    }

    @Test
    void testDeleteRepaymentStrategy_InvalidUUID() throws Exception {
        mockMvc.perform(delete("/repayment-plans/strategies/not-a-uuid"))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(repaymentPlanService, never()).deleteRepaymentStrategy(any());
    }

    // ─── POST /repayment-plans ───────────────────────────────────────────────────

    @Test
    void testCreateRepaymentPlan_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        RepaymentPlanDTO dto = new RepaymentPlanDTO();
        dto.setMonthlyBudget(new BigDecimal("5000"));
        dto.setStrategyId(strategyId);

        RepaymentPlan plan = new RepaymentPlan();
        plan.setPlanId(UUID.randomUUID());
        plan.setUserId(userId);
        plan.setMonthlyBudget(new BigDecimal("5000"));
        plan.setStrategyId(strategyId);

        when(userService.extractUserIdFromToken(anyString())).thenReturn(userId);
        when(repaymentPlanService.changeRepaymentPlan(any(UUID.class), any(BigDecimal.class), any(UUID.class)))
                .thenReturn(plan);

        mockMvc.perform(post("/repayment-plans")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyBudget").value(5000))
                .andExpect(jsonPath("$.strategyId").value(strategyId.toString()));

        verify(userService, times(1)).extractUserIdFromToken(eq("test-token"));
        verify(repaymentPlanService, times(1))
                .changeRepaymentPlan(eq(userId), eq(new BigDecimal("5000")), eq(strategyId));
    }

    @Test
    void testCreateRepaymentPlan_ValidationFail_NullBudget() throws Exception {
        RepaymentPlanDTO dto = new RepaymentPlanDTO();
        dto.setMonthlyBudget(null);     // ข้อมูลไม่ถูกต้อง
        dto.setStrategyId(UUID.randomUUID());

        mockMvc.perform(post("/repayment-plans")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(repaymentPlanService, never())
                .changeRepaymentPlan(any(), any(), any());
    }

    @Test
    void testCreateRepaymentPlan_ValidationFail_NegativeBudget() throws Exception {
        RepaymentPlanDTO dto = new RepaymentPlanDTO();
        dto.setMonthlyBudget(new BigDecimal("-100"));  // ค่าติดลบ ไม่ถูกต้อง
        dto.setStrategyId(UUID.randomUUID());

        mockMvc.perform(post("/repayment-plans")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(repaymentPlanService, never())
                .changeRepaymentPlan(any(), any(), any());
    }

    @Test
    void testCreateRepaymentPlan_ValidationFail_NullStrategyId() throws Exception {
        RepaymentPlanDTO dto = new RepaymentPlanDTO();
        dto.setMonthlyBudget(new BigDecimal("3000"));
        dto.setStrategyId(null);        // ข้อมูลไม่ถูกต้อง

        mockMvc.perform(post("/repayment-plans")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(repaymentPlanService, never())
                .changeRepaymentPlan(any(), any(), any());
    }

    @Test
    void testCreateRepaymentPlan_ZeroBudget_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        RepaymentPlanDTO dto = new RepaymentPlanDTO();
        dto.setMonthlyBudget(BigDecimal.ZERO);   // ค่า 0 ยอมรับได้ (@PositiveOrZero)
        dto.setStrategyId(strategyId);

        RepaymentPlan plan = new RepaymentPlan();
        plan.setPlanId(UUID.randomUUID());
        plan.setUserId(userId);
        plan.setMonthlyBudget(BigDecimal.ZERO);
        plan.setStrategyId(strategyId);

        when(userService.extractUserIdFromToken(anyString())).thenReturn(userId);
        when(repaymentPlanService.changeRepaymentPlan(any(UUID.class), any(BigDecimal.class), any(UUID.class)))
                .thenReturn(plan);

        mockMvc.perform(post("/repayment-plans")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyBudget").value(0));
    }
}
