package com.example.capstone.service;

import com.example.capstone.dto.BudgetDTO;
import com.example.capstone.entity.Budget;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
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

    public void createBudget(Budget budget) {
        budgetRepository.save(budget);
    }

    public Budget updateBudget(String token,UUID id, double amount) {
        UUID userId = userService.extractUserIdFromToken(token);
        Budget existingBudget = budgetRepository.findByUserIdAndBudgetId(userId,id).orElseThrow(() -> new BusinessException("Transaction not found or not owned by this user", HttpStatus.NOT_FOUND));
        existingBudget.setAmount(amount);
        return budgetRepository.save(existingBudget);
    }

    public List<BudgetDTO> getAmountFromBudget(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        List<BudgetDTO> budgetDTOs = new ArrayList<>();
        for(Budget budget : budgets) {
            BudgetDTO budgetDTO = new BudgetDTO();
            categoryRepository.findById(budget.getCategoryId()).ifPresent(category -> {
                budgetDTO.setBudgetName(category.getCategoryName());
            });
            budgetDTO.setAmount(budget.getAmount());
            if (budgetDTOs.stream().noneMatch(b -> b.getBudgetName().equals(budgetDTO.getBudgetName()))) {
                budgetDTOs.add(budgetDTO);
            }else{
                for(BudgetDTO b : budgetDTOs){
                    if(b.getBudgetName().equals(budgetDTO.getBudgetName())){
                        b.setAmount(b.getAmount() + budgetDTO.getAmount());
                    }
                }
            }
        }
        return budgetDTOs;
    }

    public void deleteBudget(UUID id) {
        budgetRepository.deleteById(id);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void clearMonthBudget() {
        budgetRepository.findAll().forEach(budget -> {
            budget.setAmount(0.0);
            budgetRepository.save(budget);
        });
    }

}