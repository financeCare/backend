package com.example.capstone.repository;

import com.example.capstone.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserId(UUID userId);
    Optional<Budget> findByUserIdAndBudgetId(UUID userId, UUID budgetId);
    List<Budget> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);
}