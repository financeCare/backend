package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanResultDTO {
    private int estimatedMonths;
    private BigDecimal totalInterest;
    private BigDecimal totalPaid;
    private List<MonthlyPlanResultDTO> monthlyResults;
}