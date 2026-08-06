package com.example.capstone.factory;

import com.example.capstone.engineImp.calculator.InterestCalculator;
import com.example.capstone.enums.InterestCalculationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InterestCalculatorFactory {

    private final Map<String, InterestCalculator> calculators;

    public InterestCalculatorFactory(Map<String, InterestCalculator> calculators) {
        this.calculators = calculators;
    }

    public InterestCalculator get(InterestCalculationType type) {
        return calculators.get(type.name());
    }
}