package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RepaymentPlanDTO {
    @NotNull(message = "Monthly budget is required")
    @PositiveOrZero(message = "Monthly budget must be zero or positive")
    private BigDecimal monthlyBudget;

    @NotNull(message = "Strategy ID is required")
    private UUID strategyId;
}
