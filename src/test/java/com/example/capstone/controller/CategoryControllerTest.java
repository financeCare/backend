package com.example.capstone.controller;

import com.example.capstone.dto.CategoryDTO;
import com.example.capstone.entity.Category;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.CategoryService;
import com.example.capstone.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetCategories() throws Exception {
        Category category = new Category();
        category.setCategoryId(1);
        category.setCategoryName("Food");
        List<Category> categories = Arrays.asList(category);

        when(categoryService.getCategoriesByUserId(anyString())).thenReturn(categories);

        mockMvc.perform(get("/categories")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Food"));
    }

    @Test
    void testGetCategoryById() throws Exception {
        Category category = new Category();
        category.setCategoryId(1);
        category.setCategoryName("Food");

        when(categoryService.getCategoryById(anyString(), anyInt())).thenReturn(category);

        mockMvc.perform(get("/categories/1")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Food"));
    }

    @Test
    void testCreateCategory() throws Exception {
        CategoryDTO dto = new CategoryDTO();
        dto.setCategoryName("Travel");
        dto.setType("EXPENSE");

        Category category = new Category();
        category.setCategoryId(2);
        category.setCategoryName("Travel");

        when(categoryService.createCategory(anyString(), any(CategoryDTO.class))).thenReturn(category);

        mockMvc.perform(post("/categories")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Travel"));
    }

    @Test
    void testUpdateCategory() throws Exception {
        CategoryDTO dto = new CategoryDTO();
        dto.setCategoryName("Updated Travel");
        dto.setType("EXPENSE");

        Category category = new Category();
        category.setCategoryId(2);
        category.setCategoryName("Updated Travel");

        when(categoryService.updateCategory(anyString(), anyInt(), any(CategoryDTO.class))).thenReturn(category);

        mockMvc.perform(put("/categories/2")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Updated Travel"));
    }

    @Test
    void testDeleteCategory() throws Exception {
        doNothing().when(categoryService).deleteCategory(anyString(), anyInt());

        mockMvc.perform(delete("/categories/1")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(categoryService, times(1)).deleteCategory(anyString(), eq(1));
    }
}
