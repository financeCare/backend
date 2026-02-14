package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "budget_per_month")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "budget_id")
    private UUID budgetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "month", nullable = false)
    private Integer month; // 1-12

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "limit_budget", nullable = false)
    private Double limitBudget;
}