package com.example.capstone.strategy.repayment;

import com.example.capstone.domain.DebtSim;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class SnowballStrategyInterface implements RepaymentStrategyInterface {

    @Override
    public DebtSim apply(List<DebtSim> debts) {
        List<DebtSim> activeDebts = debts.stream().filter(DebtSim::isActive).toList();
        if (activeDebts.isEmpty()) return null;

        List<DebtSim> informalDebts = activeDebts.stream().filter(DebtSim::isInformal).toList();
        if (!informalDebts.isEmpty()) {
            return informalDebts.stream()
                    .min(Comparator.comparing(DebtSim::getPrincipal))
                    .orElse(null);
        }

        return activeDebts.stream()
                .min(Comparator.comparing(DebtSim::getPrincipal))
                .orElse(null);
    }
}