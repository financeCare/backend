package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySimulationDetailDTO {

    private int monthNo;

    private BigDecimal interest;
    private BigDecimal penaltyInterest;

    private BigDecimal payment;

    private BigDecimal principalStart;
    private BigDecimal principalEnd;

    private boolean late;
    private boolean penaltyApplied;
    private int month;
}