package com.example.capstone.service;

import com.example.capstone.dto.CategoryDTO;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BudgetService budgetService;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private UserService userService;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CategoryService categoryService;

    private UUID userId;
    private String token;
    private Integer categoryId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
        categoryId = 1;
    }

    @Test
    void testGetCategoryById_Success() {
        Category category = new Category();
        category.setCategoryId(categoryId);
        category.setUserId(userId);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryById(token, categoryId);

        assertNotNull(result);
        assertEquals(categoryId, result.getCategoryId());
    }

    @Test
    void testGetCategoryById_NotFound() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            categoryService.getCategoryById(token, categoryId);
        });
    }

    @Test
    void testGetCategoriesByUserId() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(new Category()));

        List<Category> result = categoryService.getCategoriesByUserId(token);

        assertFalse(result.isEmpty());
        verify(categoryRepository).findByUserId(userId);
    }

    @Test
    void testCreateCategory() {
        CategoryDTO dto = new CategoryDTO("Food", "Expense");
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        
        Category savedCategory = new Category();
        savedCategory.setCategoryName(dto.getCategoryName());
        savedCategory.setType(dto.getType());
        
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        Category result = categoryService.createCategory(token, dto);

        assertNotNull(result);
        assertEquals("Food", result.getCategoryName());
        verify(budgetService).createBudget(any(Budget.class));
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void testUpdateCategory_Success() {
        CategoryDTO dto = new CategoryDTO("Updated Food", "Expense");
        Category existingCategory = new Category();
        existingCategory.setCategoryId(categoryId);
        existingCategory.setUserId(userId);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(existingCategory);

        Category result = categoryService.updateCategory(token, categoryId, dto);

        assertNotNull(result);
        assertEquals("Updated Food", result.getCategoryName());
    }

    @Test
    void testDeleteCategory_Success() {
        Category category = new Category();
        category.setCategoryId(categoryId);
        category.setUserId(userId);
        UUID budgetId = UUID.randomUUID();
        category.setBudgetId(budgetId);

        Budget budget = new Budget();
        budget.setBudgetId(budgetId);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(budgetRepository.findById(budgetId)).thenReturn(Optional.of(budget));

        categoryService.deleteCategory(token, categoryId);

        verify(transactionRepository).deleteByCategory_CategoryId(categoryId);
        verify(categoryRepository).delete(category);
        verify(budgetRepository).delete(budget);
    }
}
