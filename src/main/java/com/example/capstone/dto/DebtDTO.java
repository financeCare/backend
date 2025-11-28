package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

import jakarta.validation.constraints.*;

import java.util.Date;
import java.util.UUID;

@Data
public class DebtDTO {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @Positive(message = "Principal amount must be greater than 0")
    private double principalAmount;

    @PositiveOrZero(message = "Interest rate must be 0 or greater")
    private double interestRate;

    @NotNull(message = "Repayment type is required")
    @Positive(message = "Repayment type ID must be a positive number")
    private Integer repaymentTypeId;

    @NotNull(message = "Start date is required")
    @PastOrPresent(message = "Start date cannot be in the future")
    private Date startDate;

    @NotNull(message = "End date is required")
    private Date endDate;

    @NotNull(message = "isActive must not be null")
    private Boolean isActive;

    @Min(value = 1, message = "Priority must be at least 1")
    private int priority;

    @NotNull(message = "Debt type is required")
    @Positive(message = "Debt type ID must be a positive number")
    private Integer debtTypeId;

    @NotBlank(message = "Debt name is required")
    private String debtName;

    @AssertTrue(message = "End date must be after start date")
    private boolean isEndDateValid() {
        if (startDate == null || endDate == null) return true;
        return endDate.after(startDate);
    }
}
