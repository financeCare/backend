package com.example.capstone.service;

import com.example.capstone.dto.CategoryDTO;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BudgetService budgetService;
    private final BudgetRepository budgetRepository;
    private final UserService userService;
    private final TransactionRepository transactionRepository;

    private final double defaultBudgets = 1000;

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
        budget.setLimitBudget(defaultBudgets);
        budgetService.createBudget(budget);
        category.setUserId(userId);
        category.setCategoryName(categoryDTO.getCategoryName());
        category.setType(categoryDTO.getType());
        category.setBudgetId(budget.getBudgetId());
        return categoryRepository.save(category);
    }

    public Category updateCategory(String token,Integer categoryId, CategoryDTO categoryDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        Category category = categoryRepository.findByCategoryIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new RuntimeException("Category not found or not owned by this user"));
        category.setCategoryName(categoryDTO.getCategoryName());
        category.setType(categoryDTO.getType());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(String token,Integer categoryId) {
        UUID userId = userService.extractUserIdFromToken(token);
        Category category = categoryRepository.findByCategoryIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new RuntimeException("Category not found or not owned by this user"));
        Budget budget = budgetRepository.findById(category.getBudgetId()).orElseThrow(() -> new RuntimeException("Budget not found for this category"));
        transactionRepository.deleteByCategory_CategoryId(categoryId);
        categoryRepository.delete(category);
        budgetRepository.delete(budget);
    }
}
