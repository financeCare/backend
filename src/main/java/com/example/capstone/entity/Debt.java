    package com.example.capstone.entity;

    import jakarta.persistence.*;
    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;

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

        @Column(name = "principal_amount")
        private double principalAmount;

        @Column(name = "interest_rate")
        private double interestRate;

        @ManyToOne
        @JoinColumn(name = "repayment_type")
        private RepaymentType repaymentType;

        @Column(name = "start_date")
        private Date startDate;

        @Column(name = "end_date")
        private Date endDate;

        @Column(name = "is_active")
        private boolean isActive;

        private int priority;

        @ManyToOne
        @JoinColumn(name = "debt_type")
        private DebtType debtType;

        @Column(name = "debt_name")
        private String debtName;

        @Column(name = "min_payment")
        private double minPayment;

        @Column(name = "due_day")
        private int dueDay;

    }
