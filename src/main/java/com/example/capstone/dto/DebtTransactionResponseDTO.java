package com.example.capstone.dto;

import com.example.capstone.enums.DebtTxnType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtTransactionResponseDTO {
    private UUID transactionId;
    private DebtTxnType txnType;
    private BigDecimal amount;
    private LocalDate txnDate;
    private String description;
}
