package com.example.capstone.controller;

import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.TransactionResponse;
import com.example.capstone.entity.Transaction;
import com.example.capstone.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionResponse> getTransactions(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.getTransactionsByUserId(token);
    }

    @PostMapping
    public Transaction createTransaction(@RequestHeader("Authorization") String authorizationHeader,@RequestBody TransactionRequest transaction) {
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.createTransaction(token,transaction);
    }

    @PutMapping("/{transactionId}")
    public Transaction updateTransaction(@RequestHeader("Authorization") String authorizationHeader,@PathVariable UUID transactionId, @RequestBody TransactionRequest transaction) {
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.updateTransaction(token,transactionId, transaction);
    }

    @DeleteMapping("/{transactionId}")
    public void deleteTransaction(@RequestHeader("Authorization") String authorizationHeader,@PathVariable UUID transactionId) {
        String token = authorizationHeader.replace("Bearer ", "");
        transactionService.deleteTransaction(token,transactionId);
    }

}
