package com.example.capstone.service;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import com.example.capstone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private UserService userService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BudgetService budgetService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testCreateBudget() {
        Budget budget = new Budget();
        budgetService.createBudget(budget);
        verify(budgetRepository, times(1)).save(budget);
    }

    @Test
    void testUpdateLimitAmountBudget_Success() {
        UUID budgetId = UUID.randomUUID();
        double amount = 500.0;
        Budget budget = new Budget();
        budget.setBudgetId(budgetId);
        budget.setUserId(userId);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(budgetRepository.findByUserIdAndBudgetId(userId, budgetId)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(any(Budget.class))).thenReturn(budget);

        Budget updatedBudget = budgetService.updateLimitAmountBudget(token, budgetId, amount);

        assertNotNull(updatedBudget);
        assertEquals(amount, updatedBudget.getLimitBudget());
        verify(budgetRepository).save(budget);
    }

    @Test
    void testUpdateLimitAmountBudget_NotFound() {
        UUID budgetId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(budgetRepository.findByUserIdAndBudgetId(userId, budgetId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            budgetService.updateLimitAmountBudget(token, budgetId, 100.0);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Transaction not found or not owned by this user", exception.getMessage());
    }

    @Test
    void testGetAmountFromBudget() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        
        Budget budget = new Budget();
        budget.setBudgetId(UUID.randomUUID());
        budget.setAmount(100.0);
        budget.setLimitBudget(200.0);
        
        List<Budget> budgets = List.of(budget);
        when(budgetRepository.findByUserId(userId)).thenReturn(budgets);

        Category category = new Category();
        category.setCategoryId(1);
        category.setCategoryName("Food");
        
        when(categoryRepository.findByUserIdAndBudgetIdAndTypeNot(userId, budget.getBudgetId(), "Income"))
                .thenReturn(category);

        List<BudgetOverviewDto> result = budgetService.getAmountFromBudget(token);

        assertEquals(1, result.size());
        assertEquals("Food", result.get(0).getCategoryName());
        assertEquals(100.0, result.get(0).getAmount());
    }

    @Test
    void testDeleteBudget() {
        UUID budgetId = UUID.randomUUID();
        budgetService.deleteBudget(budgetId);
        verify(budgetRepository).deleteById(budgetId);
    }
}
