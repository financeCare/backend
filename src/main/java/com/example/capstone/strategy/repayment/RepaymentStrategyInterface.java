package com.example.capstone.strategy.repayment;

import com.example.capstone.domain.DebtSim;

import java.util.List;

public interface RepaymentStrategyInterface {

    DebtSim apply(List<DebtSim> debts);
}