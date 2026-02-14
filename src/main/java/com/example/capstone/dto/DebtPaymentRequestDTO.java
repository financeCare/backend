package com.example.capstone.dto;

import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
public class DebtPaymentRequestDTO {
    private UUID debtId;
    private double amount;
    private Date paidAt;
}
