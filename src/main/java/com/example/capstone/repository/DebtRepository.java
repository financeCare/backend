package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt,UUID > {
    @Query("SELECT d FROM Debt d LEFT JOIN FETCH d.debtType LEFT JOIN FETCH d.repaymentType WHERE d.userId = :userId")
    List<Debt> findAllByUserId(@Param("userId") UUID userId);
    Optional<Debt> findByDebtIdAndUserId(UUID debtId, UUID userId);
    Optional<Debt> findByDebtIdAndUserIdAndActiveTrue(UUID debtId, UUID userId);
    @Query("SELECT d FROM Debt d LEFT JOIN FETCH d.debtType LEFT JOIN FETCH d.repaymentType WHERE d.active = :active AND d.userId = :userId")
    List<Debt> findByActiveAndUserId(@Param("active") Boolean active, @Param("userId") UUID userId);
    List<Debt> findByActiveTrueAndDueDay(Integer dueDay);
    List<Debt> findByActiveTrue();
}
