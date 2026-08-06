package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtStatement;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebtStatementRepository extends JpaRepository<DebtStatement, UUID> {

    Optional<DebtStatement> findByDebtAndStatementYearAndStatementMonth(
            Debt debt,
            int statementYear,
            int statementMonth
    );

    List<DebtStatement> findByDebtOrderByStatementYearDescStatementMonthDesc(
            Debt debt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select statement
        from DebtStatement statement
        where statement.debt = :debt
        and statement.statementYear = :year
        and statement.statementMonth = :month
    """)
    Optional<DebtStatement> findForUpdate(
            @Param("debt") Debt debt,
            @Param("year") int year,
            @Param("month") int month
    );
}