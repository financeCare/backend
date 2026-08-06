package com.example.capstone.factory;

import com.example.capstone.engineImp.calculator.InterestCalculator;
import com.example.capstone.enums.InterestCalculationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InterestCalculatorFactoryTest {

    @Test
    public void testGetCalculator() {
        InterestCalculator calculator = mock(InterestCalculator.class);
        Map<String, InterestCalculator> calculators = new HashMap<>();
        calculators.put(InterestCalculationType.THIRTY_360.name(), calculator);

        InterestCalculatorFactory factory = new InterestCalculatorFactory(calculators);

        InterestCalculator result = factory.get(InterestCalculationType.THIRTY_360);
        assertEquals(calculator, result);
    }
}
