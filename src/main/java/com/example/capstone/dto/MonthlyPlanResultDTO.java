package com.example.capstone.dto;

//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.util.List;
//
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//public class MonthlyPlanResultDTO {
//    private int monthNo;
//    private double monthInterest;
//    private double paidThisMonth;     // จ่ายรวมเดือนนี้
//    private double remainingDebtTotal; // ยอดหนี้รวมคงเหลือหลังจ่าย
//    private List<DebtPaymentDTO> debtPayments; // จ่ายให้หนี้ไหนบ้าง
//}

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MonthlyPlanResultDTO {

    public int month;
    public BigDecimal totalInterest;
    public BigDecimal totalPaid;
    public BigDecimal remainingTotal;

    public List<DebtPaymentDTO> debtPayments;
}