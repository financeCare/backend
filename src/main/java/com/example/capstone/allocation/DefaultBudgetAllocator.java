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
            // เมื่อเงินไม่พอจ่ายขั้นต่ำ ให้จัดสรรเงินตามลำดับ Priority
            // (ตัวเลขน้อยคือสำคัญมาก)
            List<DebtSim> prioritySorted = debts.stream()
                    .sorted(Comparator.comparingInt(DebtSim::getPriority))
                    .toList();

            for (DebtSim d : prioritySorted) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0)
                    break;

                BigDecimal toOther = remaining.min(d.getMinPayment());
                map.put(d.getDebtId(), toOther);
                remaining = remaining.subtract(toOther);
            }
            return map;
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Priority 1: High Priority (Value low) goes first
            // Priority 2: Within same Priority, use Strategy Target
            UUID targetId = (target != null) ? target.getDebtId() : null;

            List<DebtSim> extraPrioritySorted = debts.stream()
                    .sorted(Comparator.comparingInt(DebtSim::getPriority)
                            .thenComparing((d1, d2) -> {
                                if (targetId == null) return 0;
                                boolean isD1Target = d1.getDebtId().equals(targetId);
                                boolean isD2Target = d2.getDebtId().equals(targetId);
                                if (isD1Target) return -1;
                                if (isD2Target) return 1;
                                return 0;
                            }))
                    .toList();

            for (DebtSim d : extraPrioritySorted) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal current = map.getOrDefault(d.getDebtId(), BigDecimal.ZERO);
                map.put(d.getDebtId(), current.add(remaining));
                remaining = BigDecimal.ZERO; 
            }
        }

        return map;
    }
}