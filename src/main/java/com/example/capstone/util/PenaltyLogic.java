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

    // เพิ่ม parameter plannedPayment เพื่อเช็คว่ายอดที่จ่ายเข้ามาน้อยกว่าขั้นต่ำหรือไม่
    public static LateResult checkAndCalculate(DebtSim debt, BigDecimal principal, BigDecimal plannedPayment) {
        if (debt.getCurrentDate() == null) {
            return new LateResult(false, BigDecimal.ZERO);
        }

        // เช็คว่าเงินที่นำมาจ่ายรวมกันน้อยกว่ายอดจ่ายขั้นต่ำที่ระบบกำหนดหรือไม่
        boolean isUnderpaid = plannedPayment.compareTo(debt.getMinPayment()) < 0;
        BigDecimal penalty = BigDecimal.ZERO;

        if (isUnderpaid) {
            // ใน Simulation สมมติว่าเมื่อจ่ายไม่ครบ ถือว่าค้างชำระเป็นเวลา 30 วัน (1 เดือน)
            long overdueDays = 30;
            if (overdueDays > debt.getPenaltyTriggerDays() && debt.getPenaltyAnnualRate() != null) {
                penalty = PenaltyCalculator.calculateMonthly(
                        principal,
                        debt.getPenaltyAnnualRate()
                );
            }
        }

        return new LateResult(isUnderpaid, penalty);
    }
}
