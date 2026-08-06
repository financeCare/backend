package com.example.capstone.entity;

import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "debt_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtTransaction {

    @Id
    @GeneratedValue
    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 50)
    private DebtTxnType txnType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    // link ไป arrears ถ้ารายการนี้เกิดจาก arrears record
    @Column(name = "reference_id")
    private UUID referenceId;
    
    @Column(name = "slip_id")
    private Long slipId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void validate() {

        if (txnType == null) {
            throw new BusinessException("Transaction type is required",
                    HttpStatus.BAD_REQUEST);
        }

        if (amount == null) {
            throw new BusinessException("Transaction amount is required",
                    HttpStatus.BAD_REQUEST);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transaction amount must be > 0",
                    HttpStatus.BAD_REQUEST);
        }

        if (txnDate == null) {
            throw new BusinessException("Transaction date is required",
                    HttpStatus.BAD_REQUEST);
        }

        // referenceId required only for charge types from arrears (optional for now to support auto-accrual)
        if ((txnType == DebtTxnType.LATE_FEE_CHARGE ||
                txnType == DebtTxnType.PENALTY_INTEREST_CHARGE) &&
                referenceId == null) {
            
            // Log warning instead of throwing if we want to track these but not block
            // For now, satisfy validation if it's not strictly from an arrears module
            // throw new BusinessException("Reference ID is required...", ...);
        }
    }
}