package com.example.capstone.controller;

import com.example.capstone.dto.BudgetDTO;
import com.example.capstone.entity.Budget;
import com.example.capstone.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budget")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService budgetService;

    @PutMapping("/{budgetId}/amount/{amount}")
    public Budget updateBudget(@RequestHeader("Authorization") String authorizationHeader,@PathVariable String budgetId,@PathVariable String amount){
        String token = authorizationHeader.replace("Bearer ", "");
        return budgetService.updateBudget(token,UUID.fromString(budgetId),Double.parseDouble(amount));
    }

    @GetMapping
    public List<BudgetDTO> getAmountFromBudget(@RequestHeader("Authorization") String authorizationHeader){
        String token = authorizationHeader.replace("Bearer ", "");
        return budgetService.getAmountFromBudget(token);
    }
}
