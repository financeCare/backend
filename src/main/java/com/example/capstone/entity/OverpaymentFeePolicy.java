package com.example.capstone.entity;

import com.example.capstone.enums.OverpaymentFeeType;
import com.example.capstone.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "overpayment_fee_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverpaymentFeePolicy {

    @Id
    @GeneratedValue
    @Column(name = "fee_policy_id")
    private UUID feePolicyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 50)
    private OverpaymentFeeType feeType;

    @Column(name = "fee_rate", precision = 5, scale = 4)
    private BigDecimal feeRate;
    // เช่น 0.02 = 2%

    @Column(name = "flat_amount", precision = 15, scale = 2)
    private BigDecimal flatAmount;

    @Column(name = "threshold_percent", precision = 5, scale = 4)
    private BigDecimal thresholdPercent;
    // เช่น 0.20 = 20%

    @Column(name = "interest_months")
    private Integer interestMonths;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void validate() {

        if (this.feeType == null) {
            throw new BusinessException(
                    "Fee type is required",
                    HttpStatus.BAD_REQUEST);
        }

        switch (this.feeType) {

            case FLAT -> {
                if (flatAmount == null || flatAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(
                            "Flat amount is required and must be greater than 0",
                            HttpStatus.BAD_REQUEST);
                }
            }

            case PERCENT_OUTSTANDING, PERCENT_PREPAID -> {
                if (feeRate == null) {
                    throw new BusinessException(
                            "Fee rate is required",
                            HttpStatus.BAD_REQUEST);
                }

                if (feeRate.compareTo(BigDecimal.ZERO) <= 0 ||
                        feeRate.compareTo(BigDecimal.ONE) > 0) {
                    throw new BusinessException(
                            "Fee rate must be between 0 and 1 (e.g. 0.02 = 2%)",
                            HttpStatus.BAD_REQUEST);
                }
            }

            case THRESHOLD -> {
                if (feeRate == null || thresholdPercent == null) {
                    throw new BusinessException(
                            "Threshold configuration is incomplete",
                            HttpStatus.BAD_REQUEST);
                }

                if (feeRate.compareTo(BigDecimal.ZERO) <= 0 ||
                        feeRate.compareTo(BigDecimal.ONE) > 0) {
                    throw new BusinessException(
                            "Fee rate must be between 0 and 1",
                            HttpStatus.BAD_REQUEST);
                }

                if (thresholdPercent.compareTo(BigDecimal.ZERO) < 0 ||
                        thresholdPercent.compareTo(BigDecimal.ONE) > 0) {
                    throw new BusinessException(
                            "Threshold percent must be between 0 and 1",
                            HttpStatus.BAD_REQUEST);
                }
            }

            case INTEREST_LOSS -> {
                if (interestMonths == null || interestMonths <= 0) {
                    throw new BusinessException(
                            "Interest months must be greater than 0",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }
    }
}
