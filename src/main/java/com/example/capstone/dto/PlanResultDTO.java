package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//public class PlanResultDTO {
//
//    private Integer estimatedMonths;
//    private double totalInterest;
//    private double totalPaid;
//
//    private List<MonthlyPlanResultDTO> monthlyResults;
//}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanResultDTO {

    public int totalMonths;
    public BigDecimal totalInterest;
    public BigDecimal totalPaid;

    public List<MonthlyPlanResultDTO> monthlyResults;
}