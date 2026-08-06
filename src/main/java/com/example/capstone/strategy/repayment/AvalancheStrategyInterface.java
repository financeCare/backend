package com.example.capstone.strategy.repayment;

import com.example.capstone.domain.DebtSim;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AvalancheStrategyInterface implements RepaymentStrategyInterface {

    @Override
    public DebtSim apply(List<DebtSim> debts) {
        List<DebtSim> activeDebts = debts.stream().filter(DebtSim::isActive).toList();
        if (activeDebts.isEmpty()) return null;

        List<DebtSim> informalDebts = activeDebts.stream().filter(DebtSim::isInformal).toList();
        if (!informalDebts.isEmpty()) {
            return informalDebts.stream()
                    .max(Comparator.comparing(DebtSim::getAnnualInterestRate))
                    .orElse(null);
        }

        return activeDebts.stream()
                .max(Comparator.comparing(DebtSim::getAnnualInterestRate))
                .orElse(null);
    }
}