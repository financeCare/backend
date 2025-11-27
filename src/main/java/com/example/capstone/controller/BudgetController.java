package com.example.capstone.controller;

import com.example.capstone.entity.Budget;
import com.example.capstone.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService budgetService;

    @PutMapping("/{budgetId}/amount/{amount}")
    public Budget updateBudget(@RequestHeader("Authorization") String authorizationHeader,@PathVariable String budgetId,@PathVariable String amount){
        String token = authorizationHeader.replace("Bearer ", "");
        return budgetService.updateBudget(token,UUID.fromString(budgetId),Double.parseDouble(amount));
    }

    //Todo
    //get budget but current money in every type.

}
