package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "debt_type")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DebtType {
    @Id
    @GeneratedValue
    @Column(name = "type_id")
    private Integer debtTypeId;
    @Column(name = "type_name")
    private String debtTypeName;
    @Column(name = "description")
    private String debtTypeDescription;
}
