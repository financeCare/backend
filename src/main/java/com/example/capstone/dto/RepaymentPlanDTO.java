package com.example.capstone.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class RepaymentPlanDTO {
    private double monthlyBudget;
    private UUID strategyId;
}
