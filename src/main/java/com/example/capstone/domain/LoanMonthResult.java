package com.example.capstone.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoanMonthResult {
    public BigDecimal principalStart;
    public BigDecimal interest;
    public BigDecimal penaltyInterest;
    public BigDecimal minPaid;
    public BigDecimal extraPaid;
    public BigDecimal principalEnd;

    public boolean late;
    public boolean penaltyApplied;
}