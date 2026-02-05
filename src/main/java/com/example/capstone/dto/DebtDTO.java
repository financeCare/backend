package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

import jakarta.validation.constraints.*;

import java.util.Date;
import java.util.UUID;

@Data
public class DebtDTO {

    private double principalAmount;

    private double interestRate;

    private Integer repaymentTypeId;

    private Date startDate;

    private Date endDate;

    private boolean isActive;

    private int priority;

    private Integer debtTypeId;

    private String debtName;

    private double minPayment;

    private int dueDay;
}
