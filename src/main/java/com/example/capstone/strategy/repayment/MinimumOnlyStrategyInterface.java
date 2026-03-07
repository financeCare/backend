package com.example.capstone.strategy.repayment;

import com.example.capstone.domain.DebtSim;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class MinimumOnlyStrategyInterface implements RepaymentStrategyInterface {

    @Override
    public DebtSim apply(List<DebtSim> debts) {
        return null;
    }
}