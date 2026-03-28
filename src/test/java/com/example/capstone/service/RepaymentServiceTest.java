package com.example.capstone.service;

import com.example.capstone.dto.DebtPaymentRequestDTO;
import com.example.capstone.entity.Debt;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtStatementRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RepaymentServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private DebtTransactionRepository debtTransactionRepository;
    @Mock
    private DebtStatementRepository debtStatementRepository;

    @InjectMocks
    private RepaymentService repaymentService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testPayDebt_Success() {
        UUID debtId = UUID.randomUUID();
        DebtPaymentRequestDTO req = new DebtPaymentRequestDTO();
        req.setDebtId(debtId);
        req.setPaymentAmount(new BigDecimal("1000"));
        req.setPaymentDate(LocalDate.now());

        Debt debt = new Debt();
        debt.setDebtId(debtId);
        debt.setUserId(userId);
        debt.setPrincipalOutstanding(new BigDecimal("5000"));

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserIdAndActiveTrue(userId, debtId)).thenReturn(Optional.of(debt));

        repaymentService.payDebt(token, req);

        verify(debtTransactionRepository, atLeastOnce()).save(any());
        verify(debtRepository).save(debt);
        assertEquals(new BigDecimal("4000"), debt.getPrincipalOutstanding());
    }

    @Test
    void testPayDebt_InvalidAmount() {
        DebtPaymentRequestDTO req = new DebtPaymentRequestDTO();
        req.setPaymentAmount(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            repaymentService.payDebt(token, req);
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testAccrueMonthlyCharges_Success() {
        Debt debt = new Debt();
        debt.setPrincipalOutstanding(new BigDecimal("10000"));
        debt.setInterestRate(new BigDecimal("0.12"));
        LocalDate date = LocalDate.of(2023, 10, 1);

        when(debtTransactionRepository.existsByDebtAndTxnTypeAndYearAndMonth(eq(debt), any(), anyInt(), anyInt()))
                .thenReturn(false);

        repaymentService.accrueMonthlyCharges(debt, date);

        verify(debtTransactionRepository, atLeastOnce()).save(any());
    }

    @Test
    void testPayDebt_SequentialMonthlyAllocation() {
        UUID debtId = UUID.randomUUID();
        DebtPaymentRequestDTO req = new DebtPaymentRequestDTO();
        req.setDebtId(debtId);
        req.setPaymentAmount(new BigDecimal("6000")); // Enough for 1 month but not quite 2
        req.setPaymentDate(LocalDate.of(2023, 11, 1));

        Debt debt = new Debt();
        debt.setDebtId(debtId);
        debt.setUserId(userId);
        debt.setPrincipalOutstanding(new BigDecimal("10000"));
        debt.setInterestRate(new BigDecimal("0.12"));
        debt.setMinPayment(new BigDecimal("5000"));
        
        // Start date is one month ago
        LocalDate startDate = LocalDate.of(2023, 10, 1);
        debt.setStartDate(java.util.Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserIdAndActiveTrue(debtId, userId)).thenReturn(Optional.of(debt));
        
        // No charges exist yet
        when(debtTransactionRepository.existsByDebtAndTxnTypeAndYearAndMonth(eq(debt), any(), anyInt(), anyInt()))
                .thenReturn(false);

        repaymentService.payDebt(token, req);

        // Verification logic:
        // 1. Month 1 (Oct): Accrues Interest (100). Pays Interest 100. Pays Principal 4900 (Total 5000).
        // 2. Month 2 (Nov): Accrues Interest (on remaining principal). Pays Interest. Pays rest to Principal.
        
        // Verify multiple saves to debtRepository (since principal is updated per month)
        verify(debtRepository, atLeast(2)).save(debt);
        // Verify multiple saves to debtTransactionRepository (PAYMENT, INTEREST_PAYMENT x2, PRINCIPAL_PAYMENT x2)
        verify(debtTransactionRepository, atLeast(5)).save(any());
    }
}
