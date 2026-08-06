package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class DebtPriorityUpdateRequestDTO {
    @NotNull(message = "Debt ID is required")
    private UUID debtId;

    @NotNull(message = "Priority is required")
    private Integer priority;
}
