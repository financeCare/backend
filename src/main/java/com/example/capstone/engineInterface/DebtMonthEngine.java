package com.example.capstone.engineInterface;

import com.example.capstone.dto.DebtSim;
import com.example.capstone.entity.Debt;

import java.math.BigDecimal;


public interface DebtMonthEngine {
    DebtMonthResult runMonth(DebtSim d, BigDecimal minPaid, BigDecimal extraPaid);
}
