package com.example.capstone.factory;

import com.example.capstone.engineInterface.DebtMonthEngine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DebtEngineFactory {

    private final List<DebtMonthEngine> engines;

    public DebtEngineFactory(List<DebtMonthEngine> engines) {
        this.engines = engines;
    }

    public DebtMonthEngine getEngine(String repaymentType) {

        return engines.stream()
                .filter(e -> e.supports(repaymentType))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No engine found for repaymentType: " + repaymentType
                        ));
    }
}