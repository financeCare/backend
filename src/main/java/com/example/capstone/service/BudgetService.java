package com.example.capstone.service;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import com.example.capstone.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.example.capstone.config.GlobalVariables.CATEGORY_SALARY;
import static com.example.capstone.config.GlobalVariables.TYPE_EXPENSE;

@Service
@AllArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserService userService;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public void createBudget(Budget budget) {
        budgetRepository.save(budget);
    }

    public Budget updateLimitAmountBudget(String token, UUID budgetId, double amount) {
        UUID userId = userService.extractUserIdFromToken(token);
        Budget existingBudget = budgetRepository.findByUserIdAndBudgetId(userId,budgetId).orElseThrow(() -> new BusinessException("Transaction not found or not owned by this user", HttpStatus.NOT_FOUND));
        existingBudget.setLimitBudget(amount);
        String categoryName = categoryRepository.findByBudgetIdAndUserId(budgetId,userId).orElseThrow(() -> new BusinessException("Category not found for this budget", HttpStatus.NOT_FOUND)).getCategoryName();
        return budgetRepository.save(existingBudget);
    }

    public List<BudgetOverviewDto> getAmountFromBudget(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        List<BudgetOverviewDto> result = new ArrayList<>();
        for (Budget budget : budgets) {
            Category relatedCategories =
                    categoryRepository.findByUserIdAndBudgetIdAndTypeNot(
                            userId,
                            budget.getBudgetId(),
                            "Income"
                    );
            if (relatedCategories == null) {
                continue;
            }
            BudgetOverviewDto dto = new BudgetOverviewDto();
            dto.setBudgetId(budget.getBudgetId().toString());
            dto.setAmount(budget.getAmount());
            dto.setLimit(budget.getLimitBudget());
            dto.setCategoryId(relatedCategories.getCategoryId());
            dto.setCategoryName(relatedCategories.getCategoryName());
            result.add(dto);
        }
        return result;
    }

    public List<BudgetOverviewDto> getTransactionOverview(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        List<BudgetOverviewDto> result = new ArrayList<>();
        for (Budget budget : budgets) {
            Category relatedCategories =
                    categoryRepository.findByUserIdAndBudgetIdAndCategoryNameNot(
                            userId,
                            budget.getBudgetId(),
                            CATEGORY_SALARY
                    );
            if (relatedCategories == null) {
                continue;
            }
            BudgetOverviewDto dto = new BudgetOverviewDto();
            dto.setBudgetId(budget.getBudgetId().toString());
            dto.setAmount(budget.getAmount());
            dto.setLimit(budget.getLimitBudget());
            dto.setCategoryId(relatedCategories.getCategoryId());
            dto.setCategoryName(relatedCategories.getCategoryName());
            result.add(dto);
        }
        return result;
    }

    public List<Budget> getAllBudgets(String token) {
    UUID userId = userService.extractUserIdFromToken(token);
        return budgetRepository.findByUserId(userId);
    }

    public double getIncomeAmount(String token){
        UUID userId = userService.extractUserIdFromToken(token);
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        double sum = 0;
        for (Budget budget : budgets) {
            Category relatedCategories =
                    categoryRepository.findByUserIdAndBudgetIdAndTypeNot(
                            userId,
                            budget.getBudgetId(),
                            TYPE_EXPENSE

                    );
            if (relatedCategories != null){
                sum = sum + budget.getAmount();
            }
        }
        return sum;
}

    public void deleteBudget(UUID id) {
        budgetRepository.deleteById(id);
    }

    @Transactional
    public void ensureBudgetMonth(UUID userId, int month, int year) {
        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(userId, month, year);
        Category category = categoryRepository.findByUserIdAndCategoryName(userId,CATEGORY_SALARY);
            for(Budget b : budgets){
                if (category.getBudgetId() != b.getBudgetId()) {
                    b.setUserId(userId);
                    b.setMonth(month);
                    b.setYear(year);
                    b.setAmount(0.0);
                    budgetRepository.save(b);
                }
            }
    }

    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Bangkok")
    @Transactional
    public void monthlyRollover() {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        int month = now.getMonthValue();
        int year = now.getYear();

        List<User> users = userRepository.findAll();

        for (User user : users) {
            ensureBudgetMonth(user.getUserId(),month,year);
        }
    }

}