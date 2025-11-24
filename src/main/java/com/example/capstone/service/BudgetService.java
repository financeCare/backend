package com.example.capstone.service;

import com.example.capstone.entity.Budget;
import com.example.capstone.repository.BudgetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BudgetService {

    @Autowired
    private BudgetRepository budgetRepository;

    public Budget createBudget(Budget budget) {
        return budgetRepository.save(budget);
    }

    public List<Budget> getBudgetsByUserId(UUID userId) {
        return budgetRepository.findByUserId(userId);
    }

    public Optional<Budget> getBudgetById(UUID id) {
        return budgetRepository.findById(id);
    }

    public Budget updateBudget(UUID id, Budget budgetDetails) {
        Optional<Budget> existingBudget = budgetRepository.findById(id);

        if (existingBudget.isPresent()) {
            Budget budgetToUpdate = existingBudget.get();
            budgetToUpdate.setAmount(budgetDetails.getAmount());
            return budgetRepository.save(budgetToUpdate);
        } else {
            throw new RuntimeException("Budget not found with id: " + id);
        }
    }

    public void deleteBudget(UUID id) {
        budgetRepository.deleteById(id);
    }
}