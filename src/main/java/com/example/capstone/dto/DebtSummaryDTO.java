package com.example.capstone.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtSummaryDTO {
    private BigDecimal principalRemaining;
    private BigDecimal interestRemaining;
    private BigDecimal lateFeeRemaining;
    private BigDecimal penaltyInterestRemaining;
    
    // Monthly remaining
    private BigDecimal interestRemainingMonth;
    private BigDecimal lateFeeRemainingMonth;
    private BigDecimal penaltyInterestRemainingMonth;

    private BigDecimal totalRemaining;

    private BigDecimal plannedPayment;
    private BigDecimal paidThisMonth;
}
