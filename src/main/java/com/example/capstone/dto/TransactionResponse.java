package com.example.capstone.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
    private UUID transactionId;
    private Double amount;
    private Date transactionDate;
    private String description;

    private CategoryDTO categoryDTO;
}
