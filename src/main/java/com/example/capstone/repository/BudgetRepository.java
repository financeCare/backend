package com.example.capstone.repository;

import com.example.capstone.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserId(UUID userId);

    List<Budget> findByUserIdAndCategoryId(UUID userId, Integer categoryId);
}