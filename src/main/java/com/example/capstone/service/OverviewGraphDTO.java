package com.example.capstone.service;

import com.example.capstone.dto.BudgetDTO;
import com.example.capstone.dto.DebtGraphDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;


@Data
@AllArgsConstructor
public class OverviewGraphDTO {
    private double income;
    private List<DebtGraphDTO> debtGraphDTO;
    private List<BudgetDTO> budgetDTO;

}
