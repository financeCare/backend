package com.example.capstone.service;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserService userService;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    public void createBudget(Budget budget) {
        budgetRepository.save(budget);
    }

    public Budget updateLimitAmountBudget(String token, UUID id, double amount) {
        UUID userId = userService.extractUserIdFromToken(token);
        Budget existingBudget = budgetRepository.findByUserIdAndBudgetId(userId,id).orElseThrow(() -> new BusinessException("Transaction not found or not owned by this user", HttpStatus.NOT_FOUND));
        existingBudget.setAmount(amount);
        String categoryName = categoryRepository.findByBudgetIdAndUserId(id,userId).orElseThrow(() -> new BusinessException("Category not found for this budget", HttpStatus.NOT_FOUND)).getCategoryName();
        notificationService.createNotificationRuleForBudget(userId,categoryName,existingBudget.getLimitBudget(),existingBudget.getAmount());
        return budgetRepository.save(existingBudget);
    }

    public List<BudgetOverviewDto> getAmountFromBudget(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        List<BudgetOverviewDto> result = new ArrayList<>();
        for (Budget budget : budgets) {
            List<Category> relatedCategories =
                    categoryRepository.findByUserIdAndBudgetIdAndTypeNot(
                            userId,
                            budget.getBudgetId(),
                            "Income"
                    );
            if (relatedCategories.isEmpty()) {
                continue;
            }
            Category category = relatedCategories.getFirst();
            BudgetOverviewDto dto = new BudgetOverviewDto();
            dto.setBudgetId(budget.getBudgetId().toString());
            dto.setAmount(budget.getAmount());
            dto.setLimit(budget.getLimitBudget());
            dto.setCategoryId(category.getCategoryId());
            dto.setCategoryName(category.getCategoryName());
            result.add(dto);
        }
        return result;
    }

    public List<Budget> getAllBudgets(String token) {
    UUID userId = userService.extractUserIdFromToken(token);
        return budgetRepository.findByUserId(userId);
    }

    public void deleteBudget(UUID id) {
        budgetRepository.deleteById(id);
    }

//    @Scheduled(cron = "0 0 0 1 * *")
//    public void clearMonthBudget() {
//        budgetRepository.findAll().forEach(budget -> {
//            budget.setAmount(0.0);
//            budgetRepository.save(budget);
//        });
//    }

}