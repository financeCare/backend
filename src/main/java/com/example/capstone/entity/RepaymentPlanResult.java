package com.example.capstone.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "repayment_plan_result")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class RepaymentPlanResult {
    @Id
    @Column(name = "plan_result_id")
    private UUID planResultId;

    @Column (name = "plan_id")
    private UUID planId;

    @Column(name = "month_no")
    private Integer monthNo;

    @Column(name ="total_payment")
    private double totalPayment;

    @Column(name = "remaining_debt")
    private double remainingDebt;

}
