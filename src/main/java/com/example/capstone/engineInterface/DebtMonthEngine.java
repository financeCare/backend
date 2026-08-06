package com.example.capstone.engineInterface;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;

import java.math.BigDecimal;


public interface DebtMonthEngine {

    boolean supports(String repaymentType);

    LoanMonthResult runMonth(
            DebtSim debt,
            BigDecimal minPayment,
            BigDecimal extraPayment
    );
}