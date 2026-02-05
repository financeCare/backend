package com.example.capstone.engineInterface;

import com.example.capstone.entity.Debt;

import java.math.BigDecimal;


public interface DebtMonthEngine {
    DebtMonthResult runMonth(Debt d, BigDecimal minPaid, BigDecimal extraPaid);
}
