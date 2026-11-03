package com.example.capstone.service;

import com.example.capstone.dto.DebtDTO;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtType;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import com.example.capstone.repository.DebtTypeRepository;
import com.example.capstone.repository.RepaymentTypeRepository;
import com.example.capstone.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    @Mock
    private DebtTypeRepository debtTypeRepository;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private RepaymentTypeRepository repaymentTypeRepository;
    @Mock
    private UserService userService;
    @Mock
    private BudgetService budgetService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DebtTransactionRepository debtTransactionRepository;
    @Mock
    private RepaymentPlanService repaymentPlanService;

    @InjectMocks
    private DebtService debtService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testAddDebt_Success() {
        DebtDTO debtDTO = new DebtDTO();
        debtDTO.setPrincipalAmount(BigDecimal.valueOf(10000));
        debtDTO.setInterestRate(BigDecimal.valueOf(5));
        debtDTO.setRepaymentTypeId(1);
        debtDTO.setDebtTypeId(1);
        debtDTO.setDebtName("Home Loan");
        debtDTO.setMinPayment(BigDecimal.valueOf(500));
        debtDTO.setInterestCalculationType(InterestCalculationType.THIRTY_360);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(repaymentTypeRepository.findById(1)).thenReturn(Optional.of(new RepaymentType()));
        when(debtTypeRepository.findById(1)).thenReturn(Optional.of(new DebtType()));

        Debt result = debtService.addDebt(token, debtDTO);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(10000), result.getPrincipalAmount());
        assertEquals("Home Loan", result.getDebtName());
        verify(debtRepository).save(any(Debt.class));
        verify(notificationService).createNotificationRuleForDebt(eq(userId), eq("Home Loan"), eq(BigDecimal.valueOf(500)), any());
    }

    @Test
    void testAddDebt_InvalidInterestRate() {
        DebtDTO debtDTO = new DebtDTO();
        debtDTO.setInterestRate(BigDecimal.valueOf(105)); // Invalid > 100

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            debtService.addDebt(token, debtDTO);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("invalid interest rate number", exception.getMessage());
    }

    @Test
    void testDeleteDebt_Success() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setDebtId(debtId);
        debt.setUserId(userId);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        Debt result = debtService.deleteDebt(token, debtId);

        assertEquals(debt, result);
        verify(debtRepository).delete(debt);
    }

    @Test
    void testGetDebtDetail_NotFound() {
        UUID debtId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserId(debtId, userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            debtService.getDebtDetail(token, debtId);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void testGetOwnDebt_summariesComeFromBatchedQueries() {
        UUID debtId1 = UUID.randomUUID();
        UUID debtId2 = UUID.randomUUID();
        Debt d1 = new Debt();
        d1.setDebtId(debtId1);
        d1.setPrincipalOutstanding(new BigDecimal("1000"));
        Debt d2 = new Debt();
        d2.setDebtId(debtId2);
        d2.setPrincipalAmount(new BigDecimal("500"));   // outstanding is null: falls back to the original amount

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findAllByUserId(userId)).thenReturn(java.util.List.of(d1, d2));
        when(repaymentPlanService.calculateCurrentMonthAllocation(userId))
                .thenReturn(java.util.Map.of(debtId1, new BigDecimal("300")));
        when(debtTransactionRepository.outstandingByDebt(any(), any())).thenReturn(java.util.Map.of(
                debtId1, java.util.Map.of(
                        com.example.capstone.enums.DebtTxnType.INTEREST_CHARGE, new BigDecimal("50"),
                        com.example.capstone.enums.DebtTxnType.LATE_FEE_CHARGE, new BigDecimal("10"))));
        when(debtTransactionRepository.monthByDebt(any(), any(), anyInt(), anyInt())).thenReturn(java.util.Map.of(
                debtId1, java.util.Map.of(com.example.capstone.enums.DebtTxnType.PAYMENT, new BigDecimal("200"))));

        java.util.List<com.example.capstone.dto.DebtResponseDTO> result = debtService.getOwnDebt(token);

        assertEquals(2, result.size());
        com.example.capstone.dto.DebtSummaryDTO s1 = result.get(0).getSummary();
        assertEquals(0, new BigDecimal("1060").compareTo(s1.getTotalRemaining()));   // 1000 + 50 + 10 + 0
        assertEquals(0, new BigDecimal("200").compareTo(s1.getPaidThisMonth()));
        assertEquals(0, new BigDecimal("300").compareTo(s1.getPlannedPayment()));
        com.example.capstone.dto.DebtSummaryDTO s2 = result.get(1).getSummary();
        assertEquals(0, new BigDecimal("500").compareTo(s2.getPrincipalRemaining()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s2.getTotalRemaining().subtract(new BigDecimal("500"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(s2.getPlannedPayment()));

        // the point of the change: one grouped query per kind, not 7 per debt
        verify(debtTransactionRepository, times(1)).outstandingByDebt(any(), any());
        verify(debtTransactionRepository, times(1)).monthByDebt(any(), any(), anyInt(), anyInt());
        verify(debtTransactionRepository, never()).sumOutstandingByType(any(), any());
        verify(debtTransactionRepository, never()).sumMonthDebt(any(), any(), anyInt(), anyInt());
    }
}
