package com.example.capstone.dto;

import com.example.capstone.enums.StrategyType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RepaymentPlanDtoV2 {

    private BigDecimal monthlyBudget;
    private StrategyType strategyType;

    // getters/setters
}