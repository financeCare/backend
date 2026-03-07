package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DebtTransactionRepository extends JpaRepository<DebtTransaction, UUID> {
    @Query("""
       SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
       FROM DebtTransaction t
       WHERE t.debt = :debt
       AND t.txnType = :type
       AND FUNCTION('YEAR', t.txnDate) = :year
       AND FUNCTION('MONTH', t.txnDate) = :month
       """)
    boolean existsByDebtAndTxnTypeAndYearAndMonth(
            Debt debt,
            DebtTxnType type,
            int year,
            int month
    );
    
    @Query("""
       SELECT t FROM DebtTransaction t
       WHERE t.debt.debtId = :debtId
       AND t.txnType = :type
       AND t.amount > 0
       ORDER BY t.txnDate ASC
       """)
    List<DebtTransaction> findOutstandingByType(
            UUID debtId,
            DebtTxnType type
    );}