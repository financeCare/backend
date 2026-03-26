package com.example.capstone.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.*;

@Data
public class TransactionRequest {

    @NotNull(message = "Category ID is required")
    @Positive(message = "Category ID must be a positive number")
    private Integer categoryId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private Double amount;

    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDateTime transactionDate;

    @Size(max = 128, message = "Description cannot exceed 128 characters")
    private String description;

    private UUID budgetId;
    
    @Size(max = 100, message = "Sender bank cannot exceed 100 characters")
    private String senderBank;

    @Size(max = 100, message = "Receiver name cannot exceed 100 characters")
    private String receiverName;

    @Size(max = 500, message = "Image path cannot exceed 500 characters")
    private String imagePath;

    private Long slipId;
}
