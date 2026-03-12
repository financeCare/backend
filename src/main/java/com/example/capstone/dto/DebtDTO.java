package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

import com.example.capstone.enums.InterestCalculationType;
import jakarta.validation.constraints.*;

@Data
public class DebtDTO {

    @NotNull(message = "Principal amount is required")
    @PositiveOrZero(message = "Principal amount must be zero or positive")
    private BigDecimal principalAmount;

    @NotNull(message = "Interest rate is required")
    @PositiveOrZero(message = "Interest rate must be zero or positive")
    private BigDecimal interestRate;

    @NotNull(message = "Repayment type is required")
    private Integer repaymentTypeId;

    @NotNull(message = "Start date is required")
    private Date startDate;

    private Date endDate;

    private boolean isActive;

    private int priority;

    @NotNull(message = "Debt type is required")
    private Integer debtTypeId;

    @NotBlank(message = "Debt name is required")
    private String debtName;

    @NotNull(message = "Minimum payment is required")
    @PositiveOrZero(message = "Minimum payment must be zero or positive")
    private BigDecimal minPayment;

    @Min(value = 1, message = "Due day must be at least 1")
    @Max(value = 31, message = "Due day must be at most 31")
    private int dueDay;

    @PositiveOrZero(message = "Penalty annual rate must be zero or positive")
    private BigDecimal penaltyAnnualRate;

    @PositiveOrZero(message = "Grace period days must be zero or positive")
    private int gracePeriodDays;

    @PositiveOrZero(message = "Penalty trigger days must be zero or positive")
    private int penaltyTriggerDays;

    private boolean isDefaulted;

    private Boolean isInformal;

    @NotNull(message = "Interest calculation type is required")
    private InterestCalculationType interestCalculationType;
}
