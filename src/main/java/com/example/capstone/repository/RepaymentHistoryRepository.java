package com.example.capstone.repository;

import com.example.capstone.entity.RepaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentHistoryRepository extends JpaRepository<RepaymentHistory, String> {
}
