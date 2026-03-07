package com.example.capstone.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PaymentAllocationResult {

    private BigDecimal paidToPenalty;
    private BigDecimal paidToInterest;
    private BigDecimal paidToPrincipal;
}