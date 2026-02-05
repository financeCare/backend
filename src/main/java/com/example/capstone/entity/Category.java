package com.example.capstone.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "category_name", nullable = false, length = 30)
    private String categoryName;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(name = "budget_id")
    private UUID budgetId;
}
