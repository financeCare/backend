package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class DebtPaymentResponseDTO {
    private UUID debtId;
    private double paidAmount;
    private double beforeBalance;
    private double afterBalance;
    private boolean closed;
}
