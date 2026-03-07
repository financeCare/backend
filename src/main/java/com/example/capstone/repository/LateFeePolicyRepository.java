package com.example.capstone.repository;

import com.example.capstone.entity.LateFeePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LateFeePolicyRepository extends JpaRepository<LateFeePolicy, UUID> {

    Optional<LateFeePolicy> findByDebt_DebtIdAndActiveTrue(UUID debtId);
}