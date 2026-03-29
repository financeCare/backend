package com.example.capstone.repository;

import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

public interface DebtTransactionRepository extends JpaRepository<DebtTransaction, UUID> {
        @Query("""
                        SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
                        FROM DebtTransaction t
                        WHERE t.debt.debtId = :debtId
                        AND t.txnType = :type
                        AND YEAR(t.txnDate) = :year
                        AND MONTH(t.txnDate) = :month
                        """)
        boolean existsByDebtIdAndTxnTypeAndYearAndMonth(
                        @Param("debtId") UUID debtId,
                        @Param("type") com.example.capstone.enums.DebtTxnType type,
                        @Param("year") int year,
                        @Param("month") int month);

        @Query("""
                        SELECT t FROM DebtTransaction t
                        WHERE t.debt.debtId = :debtId
                        AND t.txnType = :type
                        AND t.amount > 0
                        ORDER BY t.txnDate ASC
                        """)
        List<DebtTransaction> findOutstandingByType(
                        @Param("debtId") UUID debtId,
                        @Param("type") DebtTxnType type);

        @Query("""
                        SELECT COALESCE(SUM(t.amount), 0)
                        FROM DebtTransaction t
                        WHERE t.debt.debtId = :debtId
                        AND t.txnType = :type
                        AND t.amount > 0
                        """)
        BigDecimal sumOutstandingByType(
                        @Param("debtId") UUID debtId,
                        @Param("type") DebtTxnType type);

        @Query("""
                        SELECT COALESCE(SUM(t.amount), 0)
                        FROM DebtTransaction t
                        WHERE t.debt.debtId = :debtId
                        AND t.txnType = :type
                        AND t.amount > 0
                        AND YEAR(t.txnDate) = :year
                        AND MONTH(t.txnDate) = :month
                        """)
        BigDecimal sumMonthDebt(
                        @Param("debtId") UUID debtId,
                        @Param("type") DebtTxnType type,
                        @Param("year") int year,
                        @Param("month") int month);

        @Query("""
                        SELECT COALESCE(SUM(t.amount), 0)
                        FROM DebtTransaction t
                        WHERE t.debt.userId = :userId
                        AND t.txnType = 'PAYMENT'
                        AND YEAR(t.txnDate) = :year
                        AND MONTH(t.txnDate) = :month
                        """)
        BigDecimal sumPaymentsByUserIdAndMonth(
                        @Param("userId") UUID userId,
                        @Param("year") int year,
                        @Param("month") int month);

        @Query("SELECT t FROM DebtTransaction t WHERE t.debt.debtId = :debtId ORDER BY t.txnDate DESC")
        List<DebtTransaction> findByDebtDebtIdOrderByTxnDateDesc(@Param("debtId") UUID debtId);
}