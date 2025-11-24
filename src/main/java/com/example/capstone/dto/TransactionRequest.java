package com.example.capstone.dto;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
public class TransactionRequest {
    private Integer categoryId;
    private Double amount;
    private Date transactionDate;
    private String description;
    private UUID budgetId;
}
