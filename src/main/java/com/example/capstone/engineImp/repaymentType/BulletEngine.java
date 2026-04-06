package com.example.capstone.engineImp.repaymentType;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;
import com.example.capstone.engineImp.calculator.InterestCalculator;
import com.example.capstone.engineInterface.DebtMonthEngine;
import com.example.capstone.util.PenaltyLogic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BulletEngine implements DebtMonthEngine {

    @Override
    public boolean supports(String repaymentType) {
        return repaymentType.equals("BULLET");
    }

    /**
     * Bullet Payment (Interest-Only until Maturity)
     * -----------------------------------------------
     * ทุกงวด: จ่ายเฉพาะ "ดอกเบี้ยของเดือนนั้น" เพื่อให้เงินต้นคงที่
     * เมื่อครบสัญญา (isMaturityMonth = true): ปิดยอดเงินต้นทั้งก้อน
     * ถ้ามี Extra Payment เข้ามา: อนุญาตให้โปะลงเงินต้นได้ก่อนถึงกำหนด
     *
     * เทียบกับ EMI: EMI ลดเงินต้นทุกงวด แต่ Bullet ถือเงินต้นไว้เต็มจนครบกำหนด
     */
    @Override
    public LoanMonthResult runMonth(
            DebtSim debt,
            BigDecimal minPayment,
            BigDecimal extraPayment) {

        BigDecimal principalStart = debt.getPrincipal();

        // คำนวณดอกเบี้ยจากยอด Principal ปัจจุบัน
        BigDecimal interest = InterestCalculator
                .calculate(principalStart, debt.getAnnualInterestRate(), debt.getInterestType());

        BigDecimal totalPlannedPayment = minPayment.add(extraPayment);

        // ตรวจสอบค่าปรับ (ถ้าจ่ายน้อยกว่า minPayment)
        PenaltyLogic.LateResult lateResult = PenaltyLogic.checkAndCalculate(debt, totalPlannedPayment);
        BigDecimal penalty = lateResult.getPenalty();

        // ยอดค้างจากงวดก่อนหน้า
        BigDecimal outstandingCharges = debt.getInterestOutstanding()
                .add(debt.getLateFeeOutstanding())
                .add(debt.getPenaltyOutstanding());

        BigDecimal remainingToAllocate = totalPlannedPayment;

        // 1. จ่ายยอดค้างเดิมก่อน (Penalty → LateFee → Interest)
        BigDecimal paidToOldCharges = remainingToAllocate.min(outstandingCharges);
        remainingToAllocate = remainingToAllocate.subtract(paidToOldCharges);

        BigDecimal p = debt.getPenaltyOutstanding();
        BigDecimal pPaid = paidToOldCharges.min(p);
        debt.setPenaltyOutstanding(p.subtract(pPaid));

        BigDecimal l = debt.getLateFeeOutstanding();
        BigDecimal lPaid = (paidToOldCharges.subtract(pPaid)).min(l);
        debt.setLateFeeOutstanding(l.subtract(lPaid));

        BigDecimal iIdx = debt.getInterestOutstanding();
        BigDecimal iIdxPaid = (paidToOldCharges.subtract(pPaid).subtract(lPaid)).min(iIdx);
        debt.setInterestOutstanding(iIdx.subtract(iIdxPaid));

        // 2. จ่ายดอกเบี้ยและค่าปรับของเดือนนี้
        BigDecimal currentMonthCharges = interest.add(penalty);
        BigDecimal paidToCurrentCharges = remainingToAllocate.min(currentMonthCharges);
        remainingToAllocate = remainingToAllocate.subtract(paidToCurrentCharges);

        // 3. ============================================================
        // BULLET LOGIC: ตรวจสอบว่าถึงกำหนดชำระคืนเงินต้นหรือยัง
        //
        // ถ้าถึงกำหนด (currentDate >= endDate ของสัญญา):
        //   → ปิดเงินต้นทั้งก้อน
        // ถ้ายังไม่ถึงกำหนด:
        //   → เงินต้นไม่ลด ยกเว้นมี Extra Payment มาโปะ
        // ============================================================
        boolean isMaturityMonth = false;
        if (debt.getCurrentDate() != null) {
            if (debt.getEndDate() != null) {
                // ถ้า currentDate >= endDate: ถึงกำหนดต้องปิดเงินต้นทั้งก้อน
                isMaturityMonth = !debt.getCurrentDate().isBefore(debt.getEndDate());
            } else {
                // กรณีที่ user ไม่ได้กรอก endDate ในฐานข้อมูล สมมติให้อายุสัญญา 10 ปี (120 เดือน)
                isMaturityMonth = !debt.getCurrentDate().isBefore(
                        debt.getStartDate().plusMonths(120)
                );
            }
        }

        BigDecimal principalEnd;
        if (isMaturityMonth) {
            // ครบกำหนด: ปิดเงินต้นทั้งก้อนจากเงินที่เหลือหลังจ่ายดอก
            BigDecimal principalPaid = remainingToAllocate.min(principalStart);
            principalEnd = principalStart.subtract(principalPaid);
        } else {
            // ยังไม่ครบกำหนด: เงินที่เหลือ (Extra) นำไปลดเงินต้นก่อนกำหนดได้
            BigDecimal principalPaid = remainingToAllocate; // Extra payment ลดเงินต้นได้
            principalEnd = principalStart.subtract(principalPaid);
        }

        if (principalEnd.compareTo(BigDecimal.ZERO) < 0) {
            principalEnd = BigDecimal.ZERO;
        }

        debt.setPrincipal(principalEnd);

        // คำนวณ actual paid breakdown
        BigDecimal actualMinPaid = minPayment.min(totalPlannedPayment);
        BigDecimal actualExtraPaid = totalPlannedPayment.subtract(actualMinPaid);

        return new LoanMonthResult(
                principalStart,
                interest,
                penalty,
                actualMinPaid,
                actualExtraPaid,
                principalEnd,
                lateResult.isLate(),
                false);
    }
}