package com.example.capstone.entity;

import com.example.capstone.enums.ChargeTiming;
import com.example.capstone.enums.InterestBase;
import com.example.capstone.enums.LateFeeType;
import com.example.capstone.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "late_fee_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LateFeePolicy {

    @Id
    @GeneratedValue
    @Column(name = "late_fee_policy_id")
    private UUID lateFeePolicyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 50)
    private LateFeeType feeType;

    // 🅰 Flat fee
    @Column(name = "flat_amount", precision = 15, scale = 2)
    private BigDecimal flatAmount;

    // 🅱 % ของยอดค้าง
    @Column(name = "fee_rate", precision = 5, scale = 4)
    private BigDecimal feeRate;

    // 🅲 Penalty interest rate (annual)
    @Column(name = "penalty_interest_rate", precision = 5, scale = 4)
    private BigDecimal penaltyInterestRate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "charge_timing", length = 30)
    private ChargeTiming chargeTiming = ChargeTiming.AFTER_DUE_DATE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_base", length = 30)
    private InterestBase interestBase = InterestBase.PRINCIPAL_ONLY;

    @Builder.Default
    @Column(name = "is_compound")
    private Boolean compound = false;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void validate() {

        if (feeType == null) {
            throw new BusinessException("Fee type is required", HttpStatus.BAD_REQUEST);
        }

        if (flatAmount != null && flatAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Flat amount must be >= 0", HttpStatus.BAD_REQUEST);
        }

        if (feeRate != null && feeRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Fee rate must be >= 0", HttpStatus.BAD_REQUEST);
        }

        if (penaltyInterestRate != null && penaltyInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Penalty interest rate must be >= 0", HttpStatus.BAD_REQUEST);
        }

        switch (feeType) {

            case FLAT -> {
                if (flatAmount == null || flatAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("Flat amount is required and must be > 0",
                            HttpStatus.BAD_REQUEST);
                }
            }

            case PERCENT_ARREARS -> {
                if (feeRate == null || feeRate.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("Fee rate is required and must be > 0",
                            HttpStatus.BAD_REQUEST);
                }
                if (feeRate.compareTo(BigDecimal.ONE) > 0) {
                    throw new BusinessException("Fee rate must be <= 1 (e.g., 0.05)",
                            HttpStatus.BAD_REQUEST);
                }
            }

            case PENALTY_INTEREST -> {
                if (penaltyInterestRate == null ||
                        penaltyInterestRate.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("Penalty interest rate is required and must be > 0",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }
    }
}