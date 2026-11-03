package com.example.capstone.allocation;

import com.example.capstone.domain.DebtSim;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultBudgetAllocatorTest {

    private final DefaultBudgetAllocator allocator = new DefaultBudgetAllocator();

    private DebtSim debt(String minPayment, int priority) {
        DebtSim d = new DebtSim();
        d.setDebtId(UUID.randomUUID());
        d.setMinPayment(new BigDecimal(minPayment));
        d.setPriority(priority);
        return d;
    }

    private void assertAmount(String expected, Map<UUID, BigDecimal> result, DebtSim d) {
        assertEquals(0, new BigDecimal(expected).compareTo(result.get(d.getDebtId())),
                "expected " + expected + " but was " + result.get(d.getDebtId()));
    }

    @Test
    void budgetEqualToTotalMinimum_paysEachMinimumOnly() {
        DebtSim a = debt("1000", 1);
        DebtSim b = debt("500", 2);

        Map<UUID, BigDecimal> result = allocator.allocate(new BigDecimal("1500"), List.of(a, b), a);

        assertAmount("1000", result, a);
        assertAmount("500", result, b);
    }

    @Test
    void extraBudget_goesToTheStrategyTargetDebt() {
        DebtSim a = debt("1000", 1);
        DebtSim b = debt("500", 1);

        // same priority, so the strategy target (b) receives the extra 700
        Map<UUID, BigDecimal> result = allocator.allocate(new BigDecimal("2200"), List.of(a, b), b);

        assertAmount("1000", result, a);
        assertAmount("1200", result, b);
    }

    @Test
    void extraBudget_prefersLowerPriorityNumberOverTarget() {
        DebtSim first = debt("1000", 1);
        DebtSim target = debt("500", 2);

        Map<UUID, BigDecimal> result = allocator.allocate(new BigDecimal("2000"), List.of(first, target), target);

        assertAmount("1500", result, first);
        assertAmount("500", result, target);
    }

    @Test
    void extraBudget_withoutTarget_goesToFirstByPriority() {
        DebtSim a = debt("1000", 2);
        DebtSim b = debt("500", 1);

        Map<UUID, BigDecimal> result = allocator.allocate(new BigDecimal("1800"), List.of(a, b), null);

        assertAmount("1000", result, a);
        assertAmount("800", result, b);
    }

    @Test
    void underfundedBudget_paysByPriorityUntilMoneyRunsOut() {
        DebtSim high = debt("1000", 1);
        DebtSim mid = debt("800", 2);
        DebtSim low = debt("600", 3);

        Map<UUID, BigDecimal> result = allocator.allocate(new BigDecimal("1500"), List.of(low, mid, high), high);

        assertAmount("1000", result, high);
        assertAmount("500", result, mid);   // only what is left
        assertNull(result.get(low.getDebtId()));
    }

    @Test
    void underfundedBudget_neverAllocatesMoreThanTheBudget() {
        DebtSim a = debt("700", 1);
        DebtSim b = debt("700", 2);

        Map<UUID, BigDecimal> result = allocator.allocate(new BigDecimal("1000"), List.of(a, b), a);

        BigDecimal total = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("1000").compareTo(total));
    }
}
