package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Data
public class DebtDTO {
    private double principalAmount;
    private double interestRate;
    private int repaymentTypeId;
    private Date startDate;
    private Date endDate;
    @NotNull
    private Boolean isActive;
    private int priority;
    private int debtTypeId;
    private String debtName;
}
