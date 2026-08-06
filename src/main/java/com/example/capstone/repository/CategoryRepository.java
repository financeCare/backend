package com.example.capstone.repository;

import com.example.capstone.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByUserId(UUID userId);
    Optional<Category> findByCategoryIdAndUserId(Integer categoryId, UUID userId);
    List<Category> findByUserIdAndType(UUID userId, String type);
    Optional<Category> findByBudgetIdAndUserId(UUID budgetId,UUID userId);
    Category findByUserIdAndBudgetIdAndTypeNot(
            UUID userId,
            UUID budgetId,
            String type
    );
    Category findByUserIdAndBudgetIdAndCategoryNameNot(
            UUID userId,
            UUID budgetId,
            String name
    );
    Category findByUserIdAndCategoryName(
            UUID userId,
            String name
    );

    Optional<Category> findByUserIdAndTypeAndCategoryName(UUID userId, String type, String categoryName);
}
