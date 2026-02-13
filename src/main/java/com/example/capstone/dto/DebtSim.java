package com.example.capstone.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtSim {
    public UUID debtId;
    public String debtName;
    public double principalAmount;
    public double interestRate;
    public double minPayment;
    public boolean isActive;
    public int repaymentTypeId;
}
