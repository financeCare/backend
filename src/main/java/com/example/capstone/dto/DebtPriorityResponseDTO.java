package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebtPriorityResponseDTO {
    private UUID debtId;
    private String debtName;
    private int priority;
    private BigDecimal principalAmount;
}
