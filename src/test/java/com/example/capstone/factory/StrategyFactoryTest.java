package com.example.capstone.factory;

import com.example.capstone.enums.StrategyType;
import com.example.capstone.strategy.repayment.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategyFactoryTest {

    private final StrategyFactory factory = new StrategyFactory();

    @Test
    void testGetStrategy_Snowball() {
        RepaymentStrategyInterface strategy = factory.getStrategy(StrategyType.SNOWBALL);
        assertTrue(strategy instanceof SnowballStrategyInterface);
    }

    @Test
    void testGetStrategy_Avalanche() {
        RepaymentStrategyInterface strategy = factory.getStrategy(StrategyType.AVALANCHE);
        assertTrue(strategy instanceof AvalancheStrategyInterface);
    }

    @Test
    void testGetStrategy_MinimumOnly() {
        RepaymentStrategyInterface strategy = factory.getStrategy(StrategyType.MINIMUM_ONLY);
        assertTrue(strategy instanceof MinimumOnlyStrategyInterface);
    }

    @Test
    void testGetStrategy_OptimalCost() {
        RepaymentStrategyInterface strategy = factory.getStrategy(StrategyType.OPTIMAL_COST);
        assertTrue(strategy instanceof OptimalCostStrategy);
    }
}
