package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "repayment_type")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RepaymentType {
    @Id
    @GeneratedValue
    @Column(name = "type_id")
    private Integer repaymentTypeId;
    @Column(name = "type_name")
    private String repaymentTypeName;
    @Column(name = "description")
    private String repaymentTypeDescription;
}
