package com.example.capstone.engineFactory;

import com.example.capstone.constant.RepaymentTypeIds;
import com.example.capstone.engineImp.BulletEngine;
import com.example.capstone.engineImp.EmiEngine;
import com.example.capstone.engineImp.LumpSumEngine;
import com.example.capstone.engineImp.RevolvingEngine;
import com.example.capstone.engineInterface.DebtMonthEngine;

import java.util.HashMap;
import java.util.Map;

public class DebtEngineFactory {

    private final Map<Integer, DebtMonthEngine> map = new HashMap<>();

    public DebtEngineFactory() {
        map.put(RepaymentTypeIds.REVOLVING, new RevolvingEngine());
        map.put(RepaymentTypeIds.EMI, new EmiEngine());
        map.put(RepaymentTypeIds.LUMP_SUM, new LumpSumEngine());
        map.put(RepaymentTypeIds.BULLET, new BulletEngine());
    }

    public DebtMonthEngine get(int repaymentTypeId) {
        DebtMonthEngine engine = map.get(repaymentTypeId);
        if (engine == null) throw new IllegalArgumentException("Unsupported repaymentTypeId=" + repaymentTypeId);
        return engine;
    }
}
