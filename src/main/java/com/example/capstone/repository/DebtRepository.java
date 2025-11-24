package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt,Integer > {
    List<Debt> findAllByUserId(UUID userId);
    Optional<Debt> findByDebtIdAndUserId(Integer debtId, UUID userId);
}
