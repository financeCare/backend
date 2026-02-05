package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "repayment_history")
@Getter
@Setter
@AllArgsConstructor
public class RepaymentHistory {
    @Id
    @GeneratedValue
    @Column(name = "history_id")
    private String historyId;
    @Column(name = "paid_date")
    private Date paidDate;
    @Column(name = "debt_id")
    private UUID debtId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "amount_paid")
    private int amountPaid;

}
