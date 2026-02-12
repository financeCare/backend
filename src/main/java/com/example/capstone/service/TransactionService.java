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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.example.capstone.config.GlobalVariables.*;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;

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


    public List<Transaction> filterTransactionByIncome(String token){
        UUID userId = userService.extractUserIdFromToken(token);
        List<Category> incomeCategories = categoryRepository.findByUserIdAndType(userId, "Income");
        List<Transaction> allTransaction = transactionRepository.findAllByUserId(userId);
        List<Transaction> incomeTransactions = new ArrayList<>();
        for (Transaction t : allTransaction) {
            for (Category c : incomeCategories) {
                if (t.getCategory().getCategoryId().equals(c.getCategoryId())) {
                    incomeTransactions.add(t);
                }
            }
        }
        return incomeTransactions;
    }

    public Page<Transaction> getTransactionByCategory(
            String token,
            Integer categoryId,
            Pageable pageable
    ) {
        UUID userId = userService.extractUserIdFromToken(token);
        return transactionRepository.findByUserIdAndCategoryCategoryId(userId, categoryId, pageable);
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
        notificationService.createNotificationRuleForBudget(userId,category.getCategoryName(),budget.getLimitBudget(),transactionRequest.getAmount(),budget.getBudgetId());
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
        double newAmount = budget.getAmount() - existingTransaction.getAmount();
        budget.setAmount(newAmount);
        budgetRepository.save(budget);
        transactionRepository.delete(existingTransaction);
    }

    public boolean checkThisMonth(String token,Transaction transaction) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        java.time.LocalDate currentDate = java.time.LocalDate.now();
            java.time.LocalDate transactionDate = transaction.getTransactionDate().toLocalDate();
            if (transactionDate.getMonth() == currentDate.getMonth() &&
                    transactionDate.getYear() == currentDate.getYear()) {
                return true;
            }
        return false;
    }

    @Transactional
    public void addExtraIncome(String token, double amount) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Category> categories = categoryRepository.findByUserId(userId);
        for (Category category : categories) {
            if (category.getCategoryName().equals(CATEGORY_EXTRA_INCOME)) {
                Budget extraIncomeBudget = budgetRepository
                        .findByUserIdAndBudgetId(userId, category.getBudgetId())
                        .orElseThrow(() -> new BusinessException("Budget not found or not owned by this user", HttpStatus.NOT_FOUND));
                double newAmount = extraIncomeBudget.getAmount() + amount;
                extraIncomeBudget.setAmount(newAmount);
                budgetRepository.save(extraIncomeBudget);
            }
        }
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setTransactionDate(java.time.LocalDateTime.now());
        transaction.setDescription("Extra Income");
        Category incomeCategory = categoryRepository.findByUserIdAndType(userId, "Income").get(0);
        transaction.setCategory(incomeCategory);
        transactionRepository.save(transaction);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void clearBudgetForExtraIncome() {
        List<Category> categories = categoryRepository.findAll();
        for (Category category : categories) {
            if (!category.getCategoryName().equals(CATEGORY_SALARY) && !category.getCategoryName().equals(CATEGORY_SAVING)) {
                List<Budget> budgets = budgetRepository.findByUserId(category.getUserId());
                for (Budget budget : budgets) {
                    budget.setAmount(0.0);
                    budgetRepository.save(budget);
                }
            }
        }
    }


}
