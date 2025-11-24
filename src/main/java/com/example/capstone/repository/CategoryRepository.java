package com.example.capstone.repository;

import com.example.capstone.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByUserId(UUID userId);
    Optional<Category> findByCategoryIdAndUserId(Integer categoryId, UUID userId);
}
