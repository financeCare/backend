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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepaymentServiceTest {

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
        when(debtRepository.findByDebtIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

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
}
