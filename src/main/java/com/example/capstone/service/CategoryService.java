package com.example.capstone.service;

import com.example.capstone.dto.CategoryDTO;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final double DEFAULT_BUDGETS = 1000;
    private static final String CATEGORY_NOT_FOUND_MSG = "Category not found or not owned by this user";

    private final CategoryRepository categoryRepository;
    private final BudgetService budgetService;
    private final BudgetRepository budgetRepository;
    private final UserService userService;
    private final TransactionRepository transactionRepository;

    public Category getCategoryById(String token, Integer categoryId) {
        UUID userId = userService.extractUserIdFromToken(token);
        return categoryRepository.findByCategoryIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(CATEGORY_NOT_FOUND_MSG, HttpStatus.NOT_FOUND));
    }

    public List<Category> getCategoriesByUserId(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        return categoryRepository.findByUserId(userId);
    }

    public Category createCategory(String token, CategoryDTO categoryDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        Budget budget = new Budget();
        Category category = new Category();
        budget.setUserId(userId);
        budget.setAmount(0.0);
        budget.setLimitBudget(DEFAULT_BUDGETS);
        budgetService.createBudget(budget);
        category.setUserId(userId);
        category.setCategoryName(categoryDTO.getCategoryName());
        category.setType(categoryDTO.getType());
        category.setBudgetId(budget.getBudgetId());
        return categoryRepository.save(category);
    }

    public Category updateCategory(String token, Integer categoryId, CategoryDTO categoryDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        Category category = categoryRepository.findByCategoryIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(CATEGORY_NOT_FOUND_MSG, HttpStatus.BAD_REQUEST));
        category.setCategoryName(categoryDTO.getCategoryName());
        category.setType(categoryDTO.getType());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(String token, Integer categoryId) {
        UUID userId = userService.extractUserIdFromToken(token);
        Category category = categoryRepository.findByCategoryIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(CATEGORY_NOT_FOUND_MSG, HttpStatus.NOT_FOUND));
        Budget budget = budgetRepository.findById(category.getBudgetId())
                .orElseThrow(() -> new BusinessException("Budget not found for this category", HttpStatus.NOT_FOUND));
        transactionRepository.deleteByCategory_CategoryId(categoryId);
        categoryRepository.delete(category);
        budgetRepository.delete(budget);
    }
}
