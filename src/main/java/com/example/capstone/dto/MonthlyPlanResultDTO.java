package com.example.capstone.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MonthlyPlanResultDTO {
    private int monthNo;
    private BigDecimal monthInterest;
    private BigDecimal paidThisMonth;
    private BigDecimal remainingDebtTotal;
    private List<DebtPaymentDTO> debtPayments;
}