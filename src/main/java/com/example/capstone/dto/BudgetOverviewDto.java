package com.example.capstone.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class BudgetOverviewDto {
    private String budgetId;
    private Double amount;
    private Double limit;
    private Integer categoryId;
    private String categoryName;
}
