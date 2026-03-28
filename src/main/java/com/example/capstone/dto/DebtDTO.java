package com.example.capstone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import java.math.BigDecimal;
import java.util.Date;

import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.enums.PaymentInterval;
import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebtDTO {

    @NotNull(message = "Principal amount is required")
    @PositiveOrZero(message = "Principal amount must be zero or positive")
    private BigDecimal principalAmount;

    @PositiveOrZero(message = "Principal outstanding must be zero or positive")
    private BigDecimal principalOutstanding;

    @NotNull(message = "Interest rate is required")
    @PositiveOrZero(message = "Interest rate must be zero or positive")
    private BigDecimal interestRate;

    @NotNull(message = "Repayment type is required")
    private Integer repaymentTypeId;

    @NotNull(message = "Start date is required")
    private Date startDate;

    @NotNull(message = "End date is required")
    private Date endDate;

    private boolean isActive;

    private int priority;

    @NotNull(message = "Debt type is required")
    private Integer debtTypeId;

    @NotBlank(message = "Debt name is required")
    private String debtName;

    @NotNull(message = "Minimum payment is required")
    @PositiveOrZero(message = "Minimum payment must be zero or positive")
    private BigDecimal minPayment;

    @Min(value = 1, message = "Due day must be at least 1")
    @Max(value = 31, message = "Due day must be at most 31")
    private int dueDay;

    @PositiveOrZero(message = "Penalty annual rate must be zero or positive")
    private BigDecimal penaltyAnnualRate;

    @PositiveOrZero(message = "Grace period days must be zero or positive")
    private int gracePeriodDays;

    @PositiveOrZero(message = "Penalty trigger days must be zero or positive")
    private int penaltyTriggerDays;

    private boolean isDefaulted;

    private Boolean isInformal;

    @NotNull(message = "Interest calculation type is required")
    private InterestCalculationType interestCalculationType;

    private InterestInterval interestInterval;

    private PaymentInterval paymentInterval;
    
    @PositiveOrZero(message = "Initial interest remaining must be zero or positive")
    private BigDecimal initialInterestRemaining;

    @PositiveOrZero(message = "Initial late fee remaining must be zero or positive")
    private BigDecimal initialLateFeeRemaining;

    @PositiveOrZero(message = "Initial penalty interest remaining must be zero or positive")
    private BigDecimal initialPenaltyRemaining;

    public BigDecimal getPrincipalOutstandingV2() {
        return principalOutstanding;
    }

    public void setPrincipalOutstanding(BigDecimal principalOutstanding) {
        this.principalOutstanding = principalOutstanding;
    }

    public Boolean getIsInformal() {
        return isInformal;
    }

    public void setIsInformal(Boolean isInformal) {
        this.isInformal = isInformal;
    }

    public InterestInterval getInterestInterval() {
        return interestInterval;
    }

    public void setInterestInterval(InterestInterval interestInterval) {
        this.interestInterval = interestInterval;
    }

    public PaymentInterval getPaymentInterval() {
        return paymentInterval;
    }

    public void setPaymentInterval(PaymentInterval paymentInterval) {
        this.paymentInterval = paymentInterval;
    }
}
