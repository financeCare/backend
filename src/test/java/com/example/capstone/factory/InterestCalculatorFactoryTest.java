package com.example.capstone.factory;

import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.strategy.interest.InterestCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestCalculatorFactoryTest {

    @Test
    void testGetCalculator() {
        InterestCalculator calculator = mock(InterestCalculator.class);
        Map<String, InterestCalculator> calculators = new HashMap<>();
        calculators.put("thirty360", calculator);

        InterestCalculatorFactory factory = new InterestCalculatorFactory(calculators);

        InterestCalculator result = factory.get(InterestCalculationType.THIRTY_360);
        assertEquals(calculator, result);
    }
}
