package com.example.capstone.service;

import com.example.capstone.dto.CategoryDTO;
import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.TransactionResponse;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.Transaction;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;


    public TransactionResponse mapToResponse(Transaction t) {
        CategoryDTO categoryDTO = new CategoryDTO(
                t.getCategory().getCategoryName(),
                t.getCategory().getType()
        );

        return new TransactionResponse(
                t.getTransactionId(),
                t.getAmount(),
                t.getTransactionDate(),
                t.getDescription(),
                categoryDTO
        );
    }

    public List<TransactionResponse> getTransactionsByUserId(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        return transactions.stream()
                .map(this::mapToResponse)
                .toList();
    }

    public Transaction createTransaction(String token, TransactionRequest transactionRequest) {
        UUID userId = userService.extractUserIdFromToken(token);
        Category category = categoryRepository
                .findById(transactionRequest.getCategoryId())
                .orElseThrow(() -> new BusinessException(
                        "Category not found or not owned by this user",
                        HttpStatus.NOT_FOUND));
        Budget budget = budgetRepository
                .findByUserIdAndBudgetId(userId, category.getBudgetId())
                .orElseThrow(() -> new BusinessException(
                        "Budget not found or not owned by this user",
                        HttpStatus.NOT_FOUND));
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(transactionRequest.getAmount());
        transaction.setCategory(category);
        transaction.setTransactionDate(transactionRequest.getTransactionDate());
        transaction.setDescription(transactionRequest.getDescription());
        Transaction savedTransaction = transactionRepository.save(transaction);
        double newAmount = budget.getAmount() + transactionRequest.getAmount();
        budget.setAmount(newAmount);
        budgetRepository.save(budget);
        return savedTransaction;
    }

    public Transaction updateTransaction(String token, UUID transactionId, TransactionRequest transactionRequest) {
        UUID userId = userService.extractUserIdFromToken(token);
        Transaction existingTransaction = transactionRepository
                .findByTransactionIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new BusinessException("Transaction not found or not owned by this user", HttpStatus.NOT_FOUND));
        existingTransaction.setAmount(transactionRequest.getAmount());
        Category category = categoryRepository
                .findByCategoryIdAndUserId(transactionRequest.getCategoryId(), userId)
                .orElseThrow(() -> new BusinessException("Category not found or not owned by this user",
                        HttpStatus.NOT_FOUND));
        existingTransaction.setCategory(category);
        Budget budget = budgetRepository
                .findByUserIdAndBudgetId(userId, category.getBudgetId())
                .orElseThrow(() -> new BusinessException("Budget not found or not owned by this user",
                        HttpStatus.NOT_FOUND));
        double newAmount = budget.getAmount() + transactionRequest.getAmount();
        budget.setAmount(newAmount);
        budgetRepository.save(budget);
        existingTransaction.setTransactionDate(transactionRequest.getTransactionDate());
        existingTransaction.setDescription(transactionRequest.getDescription());
        return transactionRepository.save(existingTransaction);
    }

    public void deleteTransaction(String token,UUID transactionId) {
        UUID userId = userService.extractUserIdFromToken(token);
        Transaction existingTransaction = transactionRepository
                .findByTransactionIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new BusinessException("Transaction not found or not owned by this user", HttpStatus.NOT_FOUND));
        Category category = categoryRepository
                .findByCategoryIdAndUserId(existingTransaction.getCategory().getCategoryId(), userId)
                .orElseThrow(() -> new BusinessException("Category not found or not owned by this user",
                        HttpStatus.NOT_FOUND));
        Budget budget = budgetRepository
                .findByUserIdAndBudgetId(userId, category.getBudgetId())
                .orElseThrow(() -> new BusinessException("Budget not found or not owned by this user",
                        HttpStatus.NOT_FOUND));
        double newAmount = budget.getAmount() + existingTransaction.getAmount();
        budget.setAmount(newAmount);
        budgetRepository.save(budget);
        transactionRepository.delete(existingTransaction);
    }
}
