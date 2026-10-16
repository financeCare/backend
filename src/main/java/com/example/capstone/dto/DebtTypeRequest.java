package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebtTypeRequest {
    @NotBlank(message = "Debt type name is required")
    private String debtTypeName;

    @NotBlank(message = "Debt type description is required")
    private String debtTypeDescription;
}
