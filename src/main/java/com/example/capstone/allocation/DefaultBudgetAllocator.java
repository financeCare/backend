package com.example.capstone.allocation;

import com.example.capstone.domain.DebtSim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DefaultBudgetAllocator {

    public Map<UUID, BigDecimal> allocate(
            BigDecimal monthlyBudget,
            List<DebtSim> debts,
            DebtSim target
    ) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        BigDecimal remaining = monthlyBudget;

        // 1. First Pass: Handle Minimum Payments
        BigDecimal totalMin = debts.stream()
                .map(DebtSim::getMinPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (remaining.compareTo(totalMin) >= 0) {
            // We have enough move to meet all minimums
            for (DebtSim d : debts) {
                map.put(d.getDebtId(), d.getMinPayment());
            }
            remaining = remaining.subtract(totalMin);
        } else {
            // Not enough for all minimums. 
            // Strategy: Pay the 'target' debt's minimum first, then others proportionally?
            // Or just pay as much as possible to the target to avoid its (presumably high) cost.
            
            if (target != null && debts.contains(target)) {
                BigDecimal toTarget = remaining.min(target.getMinPayment());
                map.put(target.getDebtId(), toTarget);
                remaining = remaining.subtract(toTarget);
            }
            
            // Distribute remaining to others
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                for (DebtSim d : debts) {
                    if (d.equals(target)) continue;
                    BigDecimal toOther = remaining.min(d.getMinPayment());
                    map.put(d.getDebtId(), toOther);
                    remaining = remaining.subtract(toOther);
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                }
            }
            return map; // No extra payment possible
        }

        // 2. Second Pass: Extra Payment
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            if (target != null) {
                BigDecimal current = map.getOrDefault(target.getDebtId(), BigDecimal.ZERO);
                map.put(target.getDebtId(), current.add(remaining));
            } else if (!debts.isEmpty()) {
                // Distribute extra to the first debt if no target? Or skip.
                BigDecimal current = map.getOrDefault(debts.get(0).getDebtId(), BigDecimal.ZERO);
                map.put(debts.get(0).getDebtId(), current.add(remaining));
            }
        }

        return map;
    }
}