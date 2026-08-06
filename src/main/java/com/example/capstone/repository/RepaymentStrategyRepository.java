package com.example.capstone.repository;

import com.example.capstone.entity.RepaymentStrategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RepaymentStrategyRepository extends JpaRepository<RepaymentStrategy, UUID> {
}
