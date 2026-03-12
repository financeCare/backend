package com.example.capstone.service;

import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.Transaction;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserService userService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TransactionService transactionService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testCreateTransaction_Success() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(100.0);
        request.setCategoryId(1);
        request.setTransactionDate(LocalDateTime.now());
        request.setDescription("Test transaction");

        Category category = new Category();
        category.setCategoryId(request.getCategoryId());
        category.setBudgetId(UUID.randomUUID());
        category.setCategoryName("Food");

        Budget budget = new Budget();
        budget.setBudgetId(category.getBudgetId());
        budget.setAmount(500.0);
        budget.setLimitBudget(1000.0);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findById(request.getCategoryId())).thenReturn(Optional.of(category));
        when(budgetRepository.findByUserIdAndBudgetId(userId, category.getBudgetId())).thenReturn(Optional.of(budget));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionService.createTransaction(token, request);

        assertNotNull(result);
        assertEquals(100.0, result.getAmount());
        assertEquals(600.0, budget.getAmount());
        verify(transactionRepository).save(any(Transaction.class));
        verify(budgetRepository).save(budget);
        verify(notificationService).createNotificationRuleForBudget(eq(userId), eq("Food"), eq(1000.0), eq(600.0), eq(budget.getBudgetId()));
    }

    @Test
    void testCreateTransaction_CategoryNotFound() {
        TransactionRequest request = new TransactionRequest();
        request.setCategoryId(1);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findById(request.getCategoryId())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            transactionService.createTransaction(token, request);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Category not found or not owned by this user", exception.getMessage());
    }

    @Test
    void testDeleteTransaction_Success() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setAmount(100.0);
        
        Category category = new Category();
        category.setCategoryId(1);
        category.setBudgetId(UUID.randomUUID());
        transaction.setCategory(category);

        Budget budget = new Budget();
        budget.setAmount(500.0);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(transactionRepository.findByTransactionIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        when(categoryRepository.findByCategoryIdAndUserId(category.getCategoryId(), userId)).thenReturn(Optional.of(category));
        when(budgetRepository.findByUserIdAndBudgetId(userId, category.getBudgetId())).thenReturn(Optional.of(budget));

        transactionService.deleteTransaction(token, transactionId);

        assertEquals(400.0, budget.getAmount());
        verify(budgetRepository).save(budget);
        verify(transactionRepository).delete(transaction);
    }
}
