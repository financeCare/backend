package com.example.capstone.controller;

import com.example.capstone.dto.CategoryDTO;
import com.example.capstone.entity.Category;
import com.example.capstone.service.CategoryService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final CategoryService categoryService;

    @GetMapping
    public List<Category> getCategories(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return categoryService.getCategoriesByUserId(token);
    }

    @GetMapping("/{categoryId}")
    public Category getCategoryById(@RequestHeader("Authorization") String authorizationHeader, @PathVariable(name = "categoryId") Integer categoryId) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return categoryService.getCategoryById(token, categoryId);
    }

    @PostMapping
    public Category createCategory(@RequestHeader("Authorization") String authorizationHeader, @Valid @RequestBody CategoryDTO category) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return categoryService.createCategory(token, category);
    }

    @PutMapping("/{categoryId}")
    public Category updateCategory(@RequestHeader("Authorization") String authorizationHeader, @PathVariable(name = "categoryId") Integer categoryId, @Valid @RequestBody CategoryDTO category) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return categoryService.updateCategory(token, categoryId, category);
    }

    @DeleteMapping("/{categoryId}")
    public void deleteCategory(@RequestHeader("Authorization") String authorizationHeader, @PathVariable(name = "categoryId") Integer categoryId) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        categoryService.deleteCategory(token, categoryId);
    }
}
