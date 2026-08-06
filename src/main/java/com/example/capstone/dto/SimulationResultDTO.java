package com.example.capstone.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SimulationResultDTO {

    // ===============================
    // 1️⃣ Summary Timeline
    // ===============================

    private Integer estimatedMonths;        // ใช้เวลากี่เดือน
    private boolean fullyPaid;              // ปิดหมดไหม

    // ===============================
    // 2️⃣ Principal Summary
    // ===============================

    private BigDecimal principalStart;
    private BigDecimal finalPrincipal;

    // ===============================
    // 3️⃣ Cost Summary
    // ===============================

    private BigDecimal totalInterest;
    private BigDecimal totalPenaltyInterest;
    private BigDecimal totalPaid;

    private BigDecimal totalCost;           // ดอก + penalty รวม

    // ===============================
    // 4️⃣ Risk / Warning
    // ===============================

    private boolean illegalRateWarning;     // ดอกเกิน threshold
    private boolean highPenaltyWarning;     // penalty สูงผิดปกติ
    private boolean debtTrapRisk;           // จ่ายแล้วต้นไม่ลด

    // ===============================
    // 5️⃣ Behavior Summary
    // ===============================

    private Integer lateMonths;
    private Integer penaltyMonths;

    // ===============================
    // 6️⃣ Monthly Details
    // ===============================

    private List<MonthlySimulationDetailDTO> monthlyResults;
}