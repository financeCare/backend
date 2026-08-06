package com.example.capstone.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class DebtMonthResult {

    private BigDecimal principalStart;
    private BigDecimal interestAccrued;
    private BigDecimal penaltyAccrued;

    private BigDecimal paidToInterest;
    private BigDecimal paidToPenalty;
    private BigDecimal paidToPrincipal;

    private BigDecimal principalEnd;
}
