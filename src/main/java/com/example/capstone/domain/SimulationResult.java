package com.example.capstone.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class SimulationResult {

    private UUID debtId;

    private List<LoanMonthResult> months;

    private BigDecimal totalInterest;

    private BigDecimal totalPenalty;

    private BigDecimal totalPaid;

    public SimulationResult(
            UUID debtId,
            List<LoanMonthResult> months,
            BigDecimal totalInterest,
            BigDecimal totalPenalty,
            BigDecimal totalPaid) {

        this.debtId = debtId;
        this.months = months;
        this.totalInterest = totalInterest;
        this.totalPenalty = totalPenalty;
        this.totalPaid = totalPaid;
    }
}