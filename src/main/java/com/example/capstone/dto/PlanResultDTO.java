package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanResultDTO {

    private Integer estimatedMonths;
    private double totalInterest;
    private double totalPaid;

    private List<MonthlyPlanResultDTO> monthlyResults;
}
