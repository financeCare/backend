package com.example.capstone.allocation;

import com.example.capstone.domain.DebtSim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DefaultBudgetAllocator {

    public Map<UUID, BigDecimal> allocate(
            BigDecimal monthlyBudget,
            List<DebtSim> debts,
            DebtSim target
    ) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        BigDecimal remaining = monthlyBudget;

        BigDecimal totalMin = debts.stream()
                .map(DebtSim::getMinPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (remaining.compareTo(totalMin) >= 0) {
            for (DebtSim d : debts) {
                map.put(d.getDebtId(), d.getMinPayment());
            }
            remaining = remaining.subtract(totalMin);
        } else {
            if (target != null && debts.contains(target)) {
                BigDecimal toTarget = remaining.min(target.getMinPayment());
                map.put(target.getDebtId(), toTarget);
                remaining = remaining.subtract(toTarget);
            }
            
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                for (DebtSim d : debts) {
                    if (d.equals(target)) continue;
                    BigDecimal toOther = remaining.min(d.getMinPayment());
                    map.put(d.getDebtId(), toOther);
                    remaining = remaining.subtract(toOther);
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                }
            }
            return map;
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Sort debts by priority (1 is highest), then by whatever else if needed
            List<DebtSim> prioritySorted = debts.stream()
                    .sorted(Comparator.comparingInt(DebtSim::getPriority))
                    .toList();

            for (DebtSim d : prioritySorted) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                
                BigDecimal current = map.getOrDefault(d.getDebtId(), BigDecimal.ZERO);
                // In a single month, we don't necessarily know the max it can take here without the engine
                // but usually we just dump the remaining to the highest priority debt.
                // If we want to be safe and distribute if one is 'full', it's complex because we haven't run the engine yet.
                // However, the current simulator handles 'debts.removeIf(d -> d.getPrincipal() <= 0)' AFTER the month.
                // So for a single month, we usually stick to one target for the 'extra'.
                
                map.put(d.getDebtId(), current.add(remaining));
                remaining = BigDecimal.ZERO; 
            }
        }

        return map;
    }
}