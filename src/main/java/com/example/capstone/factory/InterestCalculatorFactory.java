package com.example.capstone.factory;

import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.strategy.interest.InterestCalculator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InterestCalculatorFactory {

    private final Map<String, InterestCalculator> calculators;

    public InterestCalculatorFactory(Map<String, InterestCalculator> calculators) {
        this.calculators = calculators;
    }

    public InterestCalculator get(InterestCalculationType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case DAILY_SIMPLE -> calculators.getOrDefault("dailySimple", calculators.get(type.name()));
            case DAILY_COMPOUND -> calculators.getOrDefault("dailyCompound", calculators.get(type.name()));
            case FLAT_RATE -> calculators.getOrDefault("flatRate", calculators.get(type.name()));
            case THIRTY_360 -> calculators.getOrDefault("thirty360", calculators.get(type.name()));
        };
    }
}