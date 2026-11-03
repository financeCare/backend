package com.example.capstone.controller;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.entity.Budget;
import com.example.capstone.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budget")
@RequiredArgsConstructor
@Slf4j
public class BudgetController {
    private static final String BEARER_PREFIX = "Bearer ";
    private final BudgetService budgetService;

    @PutMapping("/{budgetId}/amount/{amount}")
    public Budget updateBudget(@RequestHeader("Authorization") String authorizationHeader,
                               @PathVariable(name = "budgetId") String budgetId,
                               @PathVariable(name = "amount") String amount) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return budgetService.updateLimitAmountBudget(token, UUID.fromString(budgetId), Double.parseDouble(amount));
    }

    @GetMapping("/overview")
    public List<BudgetOverviewDto> getAmountFromBudget(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return budgetService.getAmountFromBudget(token);
    }

    @GetMapping
    public List<Budget> getBudgets(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return budgetService.getAllBudgets(token);
    }

    @GetMapping("/income-amount")
    public double getIncomeAmount(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return budgetService.getIncomeAmount(token);
    }

    @GetMapping("/salary")
    public double getUserSalary(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return budgetService.getSalary(token);
    }

    @PostMapping("/salary/{amount}")
    public void setUserSalary(@RequestHeader("Authorization") String authorizationHeader,
                              @PathVariable(name = "amount") double amount) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        budgetService.setSalary(token, amount);
    }

    @GetMapping("/transactions-overview")
    public List<BudgetOverviewDto> getTransactionOverview(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return budgetService.getTransactionOverview(token);
    }

    @GetMapping("/reset-budget")
    public void resetBudget() {
        budgetService.monthlyRollover();
    }
}
