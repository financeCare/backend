package com.example.capstone.repository;

import com.example.capstone.entity.RepaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepaymentPlanRepository extends JpaRepository<RepaymentPlan, UUID> {
    Optional<RepaymentPlan> findByPlanIdAndUserId(UUID repaymentPlanId, UUID userId);
    RepaymentPlan findByUserId(UUID userId);
}
