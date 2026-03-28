package com.example.capstone.service;

import com.example.capstone.dto.DebtPaymentRequestDTO;
import com.example.capstone.entity.Debt;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RepaymentBugReproductionTest {

    @Mock
    private UserService userService;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private DebtTransactionRepository debtTransactionRepository;

    @InjectMocks
    private RepaymentService repaymentService;

    @Test
    void testPayDebt_BugScenario() {
        UUID userId = UUID.randomUUID();
        UUID debtId = UUID.randomUUID();
        String token = "mock-token";

        DebtPaymentRequestDTO req = new DebtPaymentRequestDTO();
        req.setDebtId(debtId);
        req.setPaymentAmount(new BigDecimal("1000"));
        // จ่ายในเดือนมีนาคม (targetMonth คือ 2026-03-01)
        req.setPaymentDate(LocalDate.of(2026, 3, 28));

        Debt debt = new Debt();
        debt.setDebtId(debtId);
        debt.setUserId(userId);
        debt.setPrincipalOutstanding(new BigDecimal("5000"));
        // วันเริ่มหนี้คือวันที่ 15 มีนาคม (currentMonth เริ่มต้นคือ 2026-03-15)
        // เนื่องจาก 2026-03-15 isAfter 2026-03-01 ตรรกะในปัจจุบันจะข้ามการจ่ายเงินทำให้ยอดหนี้ไม่ลด
        LocalDate startDate = LocalDate.of(2026, 3, 15);
        debt.setStartDate(java.util.Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserIdAndActiveTrue(debtId, userId)).thenReturn(Optional.of(debt));

        repaymentService.payDebt(token, req);

        // คาดหวังว่ายอดหนี้จะลดลงเหลือ 4000
        assertEquals(new BigDecimal("4000.00"), debt.getPrincipalOutstanding().setScale(2));
    }
}
