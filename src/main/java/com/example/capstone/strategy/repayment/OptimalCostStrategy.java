package com.example.capstone.strategy.repayment;

import com.example.capstone.domain.DebtSim;
import com.example.capstone.util.DebtCostAnalyzer;

import java.util.Comparator;
import java.util.List;

public class OptimalCostStrategy implements RepaymentStrategyInterface {

    private final DebtCostAnalyzer analyzer = new DebtCostAnalyzer();

    @Override
    public DebtSim apply(List<DebtSim> debts) {
        List<DebtSim> activeDebts = debts.stream().filter(DebtSim::isActive).toList();
        if (activeDebts.isEmpty()) return null;

        List<DebtSim> informalDebts = activeDebts.stream().filter(DebtSim::isInformal).toList();
        if (!informalDebts.isEmpty()) {
            return informalDebts.stream()
                    .max(Comparator.comparing(analyzer::effectiveCost))
                    .orElse(null);
        }

        return activeDebts.stream()
                .max(Comparator.comparing(analyzer::effectiveCost))
                .orElse(null);
    }
}