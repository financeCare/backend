package com.example.capstone.repository;

import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        /** Batch version of {@link #sumOutstandingByType}: one row per (debt, type) instead of one query per debt and type. */
        @Query("""
                        SELECT t.debt.debtId, t.txnType, COALESCE(SUM(t.amount), 0)
                        FROM DebtTransaction t
                        WHERE t.debt.debtId IN :debtIds
                        AND t.txnType IN :types
                        AND t.amount > 0
                        GROUP BY t.debt.debtId, t.txnType
                        """)
        List<Object[]> sumOutstandingByDebtIds(
                        @Param("debtIds") Collection<UUID> debtIds,
                        @Param("types") Collection<DebtTxnType> types);

        /** Batch version of {@link #sumMonthDebt}. */
        @Query("""
                        SELECT t.debt.debtId, t.txnType, COALESCE(SUM(t.amount), 0)
                        FROM DebtTransaction t
                        WHERE t.debt.debtId IN :debtIds
                        AND t.txnType IN :types
                        AND t.amount > 0
                        AND YEAR(t.txnDate) = :year
                        AND MONTH(t.txnDate) = :month
                        GROUP BY t.debt.debtId, t.txnType
                        """)
        List<Object[]> sumMonthByDebtIds(
                        @Param("debtIds") Collection<UUID> debtIds,
                        @Param("types") Collection<DebtTxnType> types,
                        @Param("year") int year,
                        @Param("month") int month);

        default Map<UUID, Map<DebtTxnType, BigDecimal>> outstandingByDebt(Collection<UUID> debtIds, Collection<DebtTxnType> types) {
                return debtIds.isEmpty() ? Map.of() : group(sumOutstandingByDebtIds(debtIds, types));
        }

        default Map<UUID, Map<DebtTxnType, BigDecimal>> monthByDebt(Collection<UUID> debtIds, Collection<DebtTxnType> types, int year, int month) {
                return debtIds.isEmpty() ? Map.of() : group(sumMonthByDebtIds(debtIds, types, year, month));
        }

        private static Map<UUID, Map<DebtTxnType, BigDecimal>> group(List<Object[]> rows) {
                Map<UUID, Map<DebtTxnType, BigDecimal>> result = new HashMap<>();
                if (rows == null) return result;
                for (Object[] row : rows) {
                        result.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                                        .put((DebtTxnType) row[1], (BigDecimal) row[2]);
                }
                return result;
        }
}