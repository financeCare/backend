package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;


@Data
@AllArgsConstructor
public class OverviewGraphDTO {
    private double income;
    private List<DebtGraphDTO> debtGraphDTO;
    private List<BudgetOverviewDto> budgetDTO;

}
