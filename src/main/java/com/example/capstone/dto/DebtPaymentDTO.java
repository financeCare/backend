package com.example.capstone.dto;

//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.util.UUID;
//
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//public class DebtPaymentDTO {
//    private UUID debtId;
//    private String debtName;
//    private double beforeBalance;
//    private double interestAdded;
//    private double minPaid;
//    private double extraPaid;
//    private double afterBalance;
//}

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
}