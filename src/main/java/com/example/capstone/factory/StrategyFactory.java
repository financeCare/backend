package com.example.capstone.factory;

import com.example.capstone.enums.StrategyType;
import com.example.capstone.strategy.repayment.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class StrategyFactory {
    public RepaymentStrategyInterface getStrategy(StrategyType type) {

        return switch (type) {

            case SNOWBALL -> new SnowballStrategyInterface();

            case AVALANCHE -> new AvalancheStrategyInterface();

            case MINIMUM_ONLY -> new MinimumOnlyStrategyInterface();

            case OPTIMAL_COST -> new OptimalCostStrategy();
        };
    }
}