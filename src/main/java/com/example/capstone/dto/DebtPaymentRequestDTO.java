package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class DebtPaymentRequestDTO {
    @NotNull(message = "Debt ID is required")
    private UUID debtId;

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be greater than 0")
    private BigDecimal paymentAmount;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    private Long slipId;
}
