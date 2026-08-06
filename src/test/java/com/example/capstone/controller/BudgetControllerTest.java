package com.example.capstone.controller;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.entity.Budget;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.BudgetService;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(BudgetController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testUpdateBudget() throws Exception {
        UUID budgetId = UUID.randomUUID();
        double amount = 5000.0;
        Budget budget = new Budget();
        budget.setBudgetId(budgetId);
        budget.setAmount(amount);

        when(budgetService.updateLimitAmountBudget(anyString(), any(UUID.class), anyDouble())).thenReturn(budget);

        mockMvc.perform(put("/budget/" + budgetId + "/amount/" + amount)
                        .header("Authorization", "Bearer token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(amount));

    }

    @Test
    void testGetAmountFromBudget() throws Exception {
        BudgetOverviewDto overview = new BudgetOverviewDto();
        overview.setCategoryName("Food");
        overview.setAmount(1000.0);
        List<BudgetOverviewDto> overviews = Arrays.asList(overview);

        when(budgetService.getAmountFromBudget(anyString())).thenReturn(overviews);

        mockMvc.perform(get("/budget/overview")
                        .header("Authorization", "Bearer token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Food"));

    }

    @Test
    void testGetBudgets() throws Exception {
        Budget budget = new Budget();
        budget.setBudgetId(UUID.randomUUID());
        List<Budget> budgets = Arrays.asList(budget);

        when(budgetService.getAllBudgets(anyString())).thenReturn(budgets);

        mockMvc.perform(get("/budget")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    void testGetIncomeAmount() throws Exception {
        when(budgetService.getIncomeAmount(anyString())).thenReturn(50000.0);

        mockMvc.perform(get("/budget/income-amount")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().string("50000.0"));
    }

    @Test
    void testSetSalary() throws Exception {
        doNothing().when(budgetService).setSalary(anyString(), anyDouble());

        mockMvc.perform(post("/budget/salary/30000.0")
                        .header("Authorization", "Bearer token"))
                .andDo(print())
                .andExpect(status().isOk());


        verify(budgetService, times(1)).setSalary(anyString(), eq(30000.0));
    }

    @Test
    void testResetBudget() throws Exception {
        doNothing().when(budgetService).monthlyRollover();

        mockMvc.perform(get("/budget/reset-budget"))
                .andExpect(status().isOk());

        verify(budgetService, times(1)).monthlyRollover();
    }
}
