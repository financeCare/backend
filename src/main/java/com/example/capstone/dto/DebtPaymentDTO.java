package com.example.capstone.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DebtPaymentDTO {

    public UUID debtId;
    public String debtName;
    public BigDecimal beforeBalance;
    public BigDecimal interestAdded;
    public BigDecimal minPaid;
    public BigDecimal extraPaid;
    public BigDecimal afterBalance;
    public boolean isDefaulted;
    public boolean isNplRisk;
}