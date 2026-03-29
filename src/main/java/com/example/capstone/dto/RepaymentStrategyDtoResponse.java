package com.example.capstone.dto;

import com.example.capstone.entity.RepaymentStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RepaymentStrategyDtoResponse {
    private double actualMinPayment;
    private double monthlyBudget;
    private List<RepaymentStrategy> repaymentStrategyList;
}
