package com.example.capstone.service;

import com.example.capstone.entity.Budget;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserService userService;

    public void createBudget(Budget budget) {
        budgetRepository.save(budget);
    }

    public List<Budget> getBudgetsByUserId(UUID userId) {
        return budgetRepository.findByUserId(userId);
    }

    public Optional<Budget> getBudgetById(UUID id) {
        return budgetRepository.findById(id);
    }

    public Budget updateBudget(String token,UUID id, double amount) {
        UUID userId = userService.extractUserIdFromToken(token);
        Budget existingBudget = budgetRepository.findByUserIdAndBudgetId(userId,id).orElseThrow(() -> new BusinessException("Transaction not found or not owned by this user", HttpStatus.NOT_FOUND));
        existingBudget.setAmount(amount);
        return budgetRepository.save(existingBudget);
    }

    public void deleteBudget(UUID id) {
        budgetRepository.deleteById(id);
    }
}