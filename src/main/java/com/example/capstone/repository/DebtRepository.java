package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt,UUID > {
    List<Debt> findAllByUserId(UUID userId);
    Optional<Debt> findByDebtIdAndUserId(UUID debtId, UUID userId);
    Optional<Debt> findByDebtIdAndUserIdAndActiveTrue(UUID debtId, UUID userId);
    List<Debt> findByActiveAndUserId(Boolean active, UUID userId);
    List<Debt> findByActiveTrueAndDueDay(Integer dueDay);
    List<Debt> findByActiveTrue();
}
