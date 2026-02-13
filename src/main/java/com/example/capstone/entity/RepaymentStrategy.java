package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_strategy")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RepaymentStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(name = "strategy_name", length = 50)
    private String strategyName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_active")
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "tags")
    private String[] tags;
}
