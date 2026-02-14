package com.example.capstone.repository;

import com.example.capstone.entity.Transaction;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
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
    // รวมรายจ่าย (type = EXPENSE)
    @Query("""
        select coalesce(sum(t.amount), 0)
        from Transaction t
        where t.userId = :userId
          and t.category.type = :categoryType
          and t.transactionDate >= :start
          and t.transactionDate < :end
    """)
    Double sumByCategoryType(
            @Param("userId") UUID userId,
            @Param("categoryType") String categoryType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // รวมรายรับตามชื่อ category (SALARY / EXTRA)
    @Query("""
        select coalesce(sum(t.amount), 0)
        from Transaction t
        where t.userId = :userId
          and t.category.type = :categoryType
          and upper(t.category.categoryName) = upper(:categoryName)
          and t.transactionDate >= :start
          and t.transactionDate < :end
    """)
    Double sumIncomeByCategoryName(
            @Param("userId") UUID userId,
            @Param("categoryType") String categoryType,
            @Param("categoryName") String categoryName,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );


}
