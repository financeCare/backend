package com.example.capstone.util;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.engineImp.calculator.PenaltyCalculator;
import lombok.Data;

import java.math.BigDecimal;

public class PenaltyLogic {

    @Data
    public static class LateResult {
        private final boolean late;
        private final BigDecimal penalty;
    }

    /**
     * ตรวจสอบและคำนวณค่าปรับตามมาตรฐาน ธปท. (ประกาศ ธปท. ปี 2564)
     *
     * หลักการ: ค่าปรับคิดจาก "ยอดงวดที่ขาดชำระ (Overdue Amount)" เท่านั้น
     *
     * @param debt           ข้อมูลหนี้
     * @param plannedPayment ยอดที่ชำระจริงในเดือนนั้น
     */
    public static LateResult checkAndCalculate(DebtSim debt, BigDecimal plannedPayment) {
        if (debt.getCurrentDate() == null || debt.getStartDate() == null) {
            return new LateResult(false, BigDecimal.ZERO);
        }

        // คำนวณยอดที่ขาดการชำระ (Overdue Amount)
        BigDecimal minPayment = debt.getMinPayment() != null ? debt.getMinPayment() : BigDecimal.ZERO;
        BigDecimal overdueAmount = minPayment.subtract(plannedPayment);

        // ถ้าจ่ายครบหรือมากกว่าขั้นต่ำ ไม่มีค่าปรับ
        if (overdueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new LateResult(false, BigDecimal.ZERO);
        }

        // คำนวณวันค้างชำระ (Overdue Days)
        // สมมติว่า Due Date คือวันที่ dueDay ของเดือนปัจจุบัน
        java.time.LocalDate dueDate = debt.getCurrentDate().withDayOfMonth(
                Math.min(debt.getDueDay() > 0 ? debt.getDueDay() : 1, debt.getCurrentDate().lengthOfMonth())
        );

        long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(dueDate, debt.getCurrentDate());
        
        // ถ้ายังไม่ถึงวันครบกำหนด หรืออยู่ในระยะผ่อนผัน (Grace Period)
        if (overdueDays <= debt.getGracePeriodDays()) {
            return new LateResult(true, BigDecimal.ZERO); // Mark as late but no penalty yet
        }

        BigDecimal penalty = BigDecimal.ZERO;
        if (overdueDays > debt.getPenaltyTriggerDays() && debt.getPenaltyAnnualRate() != null) {
            // คิดค่าปรับจากยอดที่ขาด (Overdue Amount) เป็นจำนวนวัน
            penalty = com.example.capstone.engineImp.calculator.PenaltyCalculator.calculateDaily(
                    overdueAmount,
                    debt.getPenaltyAnnualRate(),
                    (int) overdueDays
            );
        }

        return new LateResult(true, penalty);
    }
}
