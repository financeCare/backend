package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RepaymentStrategyDTO {
    @NotBlank(message = "Strategy name is required")
    private String strategyName;

    @NotBlank(message = "Description is required")
    private String description;

    private Boolean isActive;
}
