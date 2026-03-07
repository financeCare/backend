package com.example.capstone.domain;


import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.enums.RepaymentTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class DebtSim {
    private UUID debtId;
    private String debtName;
    private BigDecimal principal;
    private BigDecimal originalPrincipal;
    private BigDecimal annualInterestRate;
    private BigDecimal minPayment;
    private RepaymentTypeEnum repaymentType;
    private InterestCalculationType interestType;
    private BigDecimal penaltyRate;
    private LocalDate startDate;
    private LocalDate currentDate;
    private boolean defaulted;
    private boolean isActive;
    private BigDecimal penaltyAnnualRate;
    private int dueDay;            // วันที่ต้องจ่าย เช่น 5 ของเดือน
    private int gracePeriodDays;   // ผ่อนผัน เช่น 5 วัน
    private int penaltyTriggerDays;
    private boolean isInformal;

    // convenience function
    public boolean isPaidOff() {
        return principal != null && principal.compareTo(BigDecimal.ZERO) <= 0;
    }

}