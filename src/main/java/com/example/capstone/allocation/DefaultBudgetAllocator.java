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
            DebtSim target) {
        Map<UUID, BigDecimal> map = new HashMap<>();

        BigDecimal totalMin = debts.stream()
                .map(DebtSim::getMinPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (monthlyBudget.compareTo(totalMin) < 0) {
            allocateUnderfundedBudget(monthlyBudget, debts, map);
            return map;
        }

        for (DebtSim d : debts) {
            map.put(d.getDebtId(), d.getMinPayment());
        }

        BigDecimal remaining = monthlyBudget.subtract(totalMin);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            allocateExtraBudget(remaining, debts, target, map);
        }

        return map;
    }

    private void allocateUnderfundedBudget(BigDecimal budget, List<DebtSim> debts, Map<UUID, BigDecimal> map) {
        BigDecimal remaining = budget;
        List<DebtSim> prioritySorted = debts.stream()
                .sorted(Comparator.comparingInt(DebtSim::getPriority))
                .toList();

        for (DebtSim d : prioritySorted) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal toPay = remaining.min(d.getMinPayment());
            map.put(d.getDebtId(), toPay);
            remaining = remaining.subtract(toPay);
        }
    }

    private void allocateExtraBudget(BigDecimal remaining, List<DebtSim> debts, DebtSim target, Map<UUID, BigDecimal> map) {
        UUID targetId = (target != null) ? target.getDebtId() : null;

        debts.stream()
                .sorted(Comparator.comparingInt(DebtSim::getPriority)
                        .thenComparing((d1, d2) -> compareTarget(d1, d2, targetId)))
                .findFirst()
                .ifPresent(d -> {
                    BigDecimal current = map.getOrDefault(d.getDebtId(), BigDecimal.ZERO);
                    map.put(d.getDebtId(), current.add(remaining));
                });
    }

    private int compareTarget(DebtSim d1, DebtSim d2, UUID targetId) {
        if (targetId == null) {
            return 0;
        }
        boolean isD1Target = d1.getDebtId().equals(targetId);
        boolean isD2Target = d2.getDebtId().equals(targetId);
        if (isD1Target) {
            return -1;
        }
        if (isD2Target) {
            return 1;
        }
        return 0;
    }
}