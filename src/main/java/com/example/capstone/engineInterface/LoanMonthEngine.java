package com.example.capstone.engineInterface;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.domain.LoanMonthResult;

import java.math.BigDecimal;

public interface LoanMonthEngine {
    LoanMonthResult simulateMonth(
            DebtSim debt,
            BigDecimal extraPayment
    );
}