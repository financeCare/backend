package com.example.capstone.repository;

import com.example.capstone.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserId(UUID userId);
    void deleteByCategory_CategoryId(Integer categoryId);
    Optional<Transaction> findByTransactionIdAndUserId(UUID transactionId, UUID userId);
    List<Transaction> findAllByUserId(UUID userId);
    Page<Transaction> findByUserIdAndCategoryCategoryId(
            UUID userId,
            Integer categoryId,
            Pageable pageable
    );

}
