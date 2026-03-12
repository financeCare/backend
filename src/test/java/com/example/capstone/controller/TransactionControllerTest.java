package com.example.capstone.controller;
import java.time.LocalDateTime;


import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.TransactionResponse;
import com.example.capstone.entity.Transaction;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.TransactionService;
import com.example.capstone.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetTransactions() throws Exception {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(UUID.randomUUID());
        List<TransactionResponse> responses = Arrays.asList(response);

        when(transactionService.getTransactionsByUserId(anyString())).thenReturn(responses);

        mockMvc.perform(get("/transactions")
                        .header("Authorization", "Bearer token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());

    }

    @Test
    void testGetTransactionsByCategoryId() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setAmount(100.0);
        Page<Transaction> page = new PageImpl<>(Arrays.asList(transaction));

        when(transactionService.getTransactionByCategory(anyString(), anyInt(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/transactions/category/1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(100.0));
    }

    @Test
    void testFilterTransactionByIncome() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setAmount(500.0);
        List<Transaction> transactions = Arrays.asList(transaction);

        when(transactionService.filterTransactionByIncome(anyString())).thenReturn(transactions);

        mockMvc.perform(get("/transactions/filterByIncome")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(500.0));
    }

    @Test
    void testCreateTransaction() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(200.0);
        request.setCategoryId(1);
        request.setTransactionDate(LocalDateTime.now());

        Transaction transaction = new Transaction();
        transaction.setAmount(200.0);

        when(transactionService.createTransaction(anyString(), any(TransactionRequest.class))).thenReturn(transaction);

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200.0));

    }

    @Test
    void testUpdateTransaction() throws Exception {
        UUID transactionId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest();
        request.setAmount(300.0);
        request.setCategoryId(1);
        request.setTransactionDate(LocalDateTime.now());

        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setAmount(300.0);

        when(transactionService.updateTransaction(anyString(), any(UUID.class), any(TransactionRequest.class))).thenReturn(transaction);

        mockMvc.perform(put("/transactions/" + transactionId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(300.0));

    }

    @Test
    void testDeleteTransaction() throws Exception {
        UUID transactionId = UUID.randomUUID();
        doNothing().when(transactionService).deleteTransaction(anyString(), any(UUID.class));

        mockMvc.perform(delete("/transactions/" + transactionId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(transactionService, times(1)).deleteTransaction(anyString(), eq(transactionId));
    }
}
