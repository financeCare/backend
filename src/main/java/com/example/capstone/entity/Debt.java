    package com.example.capstone.entity;

    import com.example.capstone.enums.InterestCalculationType;
    import jakarta.persistence.*;
    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;

    import java.math.BigDecimal;
    import java.util.Date;
    import java.util.UUID;

    @Entity
    @Table(name = "debt")
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public class Debt {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "debt_id")
        private UUID debtId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @ManyToOne
        @JoinColumn(name = "repayment_type")
        private RepaymentType repaymentType;

        @Column(name = "start_date")
        private Date startDate;

        @Column(name = "end_date")
        private Date endDate;

        @Column(name = "is_active")
        private Boolean active;

        private Integer priority;

        @ManyToOne
        @JoinColumn(name = "debt_type")
        private DebtType debtType;

        @Column(name = "debt_name")
        private String debtName;

        @Column(name = "principal_amount", precision = 15, scale = 2)
        private BigDecimal principalAmount;

        @Column(name = "interest_rate", precision = 10, scale = 4)
        private BigDecimal interestRate;

        @Column(name = "min_payment", precision = 15, scale = 2)
        private BigDecimal minPayment;

        @Column(name = "due_day")
        private Integer dueDay;

        @Column(name = "principal_outstanding", precision = 15, scale = 2)
        private BigDecimal principalOutstanding;

        @Column(name = "overpayment_balance", precision = 15, scale = 2)
        private BigDecimal overpaymentBalance = BigDecimal.ZERO;

        @Column(name = "interest_type")
        private InterestCalculationType interestCalculationType;

        @Column(name = "penalty_annual_rate", precision = 10, scale = 4)
        private BigDecimal penaltyAnnualRate;

        @Column(name = "grace_period_days")
        private Integer gracePeriodDays;

        @Column(name = "penalty_trigger_days")
        private Integer penaltyTriggerDays;

        @Column(name = "is_defaulted")
        private Boolean defaulted;

        @Column(name = "is_informal")
        private Boolean isInformal = false;
    }
