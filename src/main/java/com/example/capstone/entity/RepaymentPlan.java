package com.example.capstone.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_plan")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class RepaymentPlan {

    @Id
    @Column (name = "plan_id")
    private UUID planId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name ="monthly_budget")
    private double monthlyBudget;

    @Column(name = "strategy_id")
    private UUID strategyId;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
