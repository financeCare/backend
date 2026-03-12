package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
    private UUID transactionId;
    private Double amount;
    private LocalDateTime transactionDate;
    private String description;
    private CategoryDTO categoryDTO;
}
