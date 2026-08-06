package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class BudgetDTO {
    @NotBlank(message = "Budget name is required")
    private String budgetName;

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Amount must be zero or positive")
    private Double amount;

    @NotNull(message = "Limit budget is required")
    @PositiveOrZero(message = "Limit budget must be zero or positive")
    private Double limitBudget;
}
