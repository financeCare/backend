package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "repayment_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RepaymentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id")
    private UUID historyId;
    @Column(name = "paid_date")
    private Date paidDate;
    @Column(name = "debt_id")
    private UUID debtId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "amount_paid")
    private double amountPaid;
}
