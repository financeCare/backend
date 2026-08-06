package com.example.capstone.allocation;

import com.example.capstone.domain.DebtSim;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OptimalBudgetAllocator {

    public Map<UUID, BigDecimal> allocate(
            BigDecimal budget,
            List<DebtSim> debts,
            DebtSim target
    ) {

        Map<UUID, BigDecimal> map = new HashMap<>();

        if (target == null) {
            return map;
        }

        map.put(target.getDebtId(), budget);

        return map;
    }
}