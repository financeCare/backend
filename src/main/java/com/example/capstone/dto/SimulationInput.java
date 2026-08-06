package com.example.capstone.dto;

import com.example.capstone.enums.InterestCalculationType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SimulationInput {

    // ===============================
    // 1️⃣ เงินต้น
    // ===============================

    // เงินต้นคงเหลือตอนเริ่ม simulation
    private BigDecimal principalStart;

    // เงินต้นตอนกู้ครั้งแรก (ใช้กับ flat rate)
    private BigDecimal originalPrincipal;

    // ===============================
    // 2️⃣ ดอกเบี้ยหลัก
    // ===============================

    // ดอกเบี้ยรายปี (เช่น 0.12 = 12%)
    private BigDecimal annualInterestRate;

    // ประเภทการคิดดอก
    private InterestCalculationType interestType;

    // เปิด/ปิด compound
    private boolean compoundInterest;

    // ===============================
    // 3️⃣ การชำระ
    // ===============================

    // จ่ายต่อเดือน
    private BigDecimal monthlyPayment;

    // ถ้า null = จ่ายปลายเดือน
    private Integer paymentDayInMonth;

    // จ่ายแล้วลดต้นไหม (บางหนี้ตัดแต่ดอก)
    private boolean paymentReducePrincipal;

    // ===============================
    // 4️⃣ Due / Grace
    // ===============================

    private int dueDay;               // วันครบกำหนด
    private int gracePeriodDays;      // ผ่อนผันกี่วัน

    // ===============================
    // 5️⃣ Penalty
    // ===============================

    private int penaltyTriggerDays;   // เกินกี่วันถึงคิด penalty

    private BigDecimal penaltyAnnualRate;

    // penalty แบบรายวันไหม
    private boolean penaltyDaily;

    // ===============================
    // 6️⃣ Simulation Control
    // ===============================

    private int monthsToSimulate;
    private LocalDate startDate;

    // ===============================
    // 7️⃣ Optional (สำหรับ advanced)
    // ===============================

    // ใช้ตรวจดอกเกินกฎหมาย
    private BigDecimal legalRateThreshold = new BigDecimal("0.15");

}