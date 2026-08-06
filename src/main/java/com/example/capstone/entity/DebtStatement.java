package com.example.capstone.entity;

import com.example.capstone.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "debt_statement",
        uniqueConstraints = @UniqueConstraint(name = "uk_statement", columnNames = {"debt_id", "statement_year", "statement_month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtStatement {

    @Id
    @GeneratedValue
    @Column(name = "statement_id")
    private UUID statementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(name = "statement_year", nullable = false)
    private Integer statementYear;

    @Column(name = "statement_month", nullable = false)
    private Integer statementMonth;

    @Column(name = "principal_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalSnapshot;

    @Column(name = "interest_charged", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestCharged;

    @Column(name = "interest_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestPaid;

    @Column(name = "interest_outstanding", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestOutstanding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "principal_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalPaid;

    @Column(name = "principal_closing_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalClosingBalance;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.interestCharged == null) this.interestCharged = BigDecimal.ZERO;
        if (this.interestPaid == null) this.interestPaid = BigDecimal.ZERO;
        if (this.interestOutstanding == null) this.interestOutstanding = BigDecimal.ZERO;
        if (this.principalSnapshot == null) this.principalSnapshot = BigDecimal.ZERO;
        if (this.principalPaid == null) this.principalPaid = BigDecimal.ZERO;
        if (this.principalClosingBalance == null) this.principalClosingBalance = this.principalSnapshot;
    }
    public void validate() {
        if (statementYear <= 0) {
            throw new BusinessException("Statement year is invalid", HttpStatus.BAD_REQUEST);
        }
        if (statementMonth < 1 || statementMonth > 12) {
            throw new BusinessException("Statement month is invalid", HttpStatus.BAD_REQUEST);
        }
        if (principalSnapshot == null) {
            throw new BusinessException("Principal snapshot is required", HttpStatus.BAD_REQUEST);
        }
        if (interestCharged == null || interestPaid == null || interestOutstanding == null) {
            throw new BusinessException("Interest fields are required", HttpStatus.BAD_REQUEST);
        }
        if (interestCharged.compareTo(BigDecimal.ZERO) < 0 ||
                interestPaid.compareTo(BigDecimal.ZERO) < 0 ||
                interestOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Interest fields must be >= 0", HttpStatus.BAD_REQUEST);
        }
        if (interestPaid.compareTo(interestCharged) > 0) {
            throw new BusinessException("Interest paid cannot exceed interest charged",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal calculatedOutstanding =
                interestCharged.subtract(interestPaid);

        if (calculatedOutstanding.compareTo(interestOutstanding) != 0) {
            throw new BusinessException("Interest outstanding mismatch",
                    HttpStatus.BAD_REQUEST);
        }
    }
}