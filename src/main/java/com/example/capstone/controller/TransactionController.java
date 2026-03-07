package com.example.capstone.controller;

import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.TransactionResponse;
import com.example.capstone.entity.Transaction;
import com.example.capstone.service.TransactionService;
import com.example.capstone.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserService userService;

    @GetMapping
    public List<TransactionResponse> getTransactions(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.getTransactionsByUserId(token);
    }

    @GetMapping("/category/{categoryId}")
    public Page<Transaction> getTransactionsByCategoryId(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Integer categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "transactionDate") String sortBy){
        String token = authorizationHeader.replace("Bearer ", "");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        return transactionService.getTransactionByCategory(token,categoryId,pageable);
    }

    @GetMapping("/filterByIncome")
    public List<Transaction> filterTransactionByIncome(
            @RequestHeader("Authorization") String authorizationHeader){
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.filterTransactionByIncome(token);
    }

    @PostMapping
    public Transaction createTransaction(@RequestHeader("Authorization") String authorizationHeader,@Valid @RequestBody TransactionRequest transaction) {
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.createTransaction(token,transaction);
    }

    @PutMapping("/{transactionId}")
    public Transaction updateTransaction(@RequestHeader("Authorization") String authorizationHeader,@PathVariable UUID transactionId,@Valid @RequestBody TransactionRequest transaction) {
        String token = authorizationHeader.replace("Bearer ", "");
        return transactionService.updateTransaction(token,transactionId, transaction);
    }

    @DeleteMapping("/{transactionId}")
    public void deleteTransaction(@RequestHeader("Authorization") String authorizationHeader,@PathVariable UUID transactionId) {
        String token = authorizationHeader.replace("Bearer ", "");
        transactionService.deleteTransaction(token,transactionId);
    }

}
