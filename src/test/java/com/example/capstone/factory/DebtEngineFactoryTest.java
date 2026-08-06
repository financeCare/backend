package com.example.capstone.factory;

import com.example.capstone.engineInterface.DebtMonthEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebtEngineFactoryTest {

    @Test
    void testGetEngine_Success() {
        DebtMonthEngine engine1 = mock(DebtMonthEngine.class);
        DebtMonthEngine engine2 = mock(DebtMonthEngine.class);

        when(engine1.supports("TYPE_A")).thenReturn(false);
        when(engine2.supports("TYPE_A")).thenReturn(true);

        List<DebtMonthEngine> engines = Arrays.asList(engine1, engine2);
        DebtEngineFactory factory = new DebtEngineFactory(engines);

        DebtMonthEngine result = factory.getEngine("TYPE_A");
        assertEquals(engine2, result);
    }

    @Test
    void testGetEngine_NotFound_ThrowsException() {
        DebtMonthEngine engine = mock(DebtMonthEngine.class);
        when(engine.supports("TYPE_A")).thenReturn(false);

        List<DebtMonthEngine> engines = Collections.singletonList(engine);
        DebtEngineFactory factory = new DebtEngineFactory(engines);

        assertThrows(IllegalArgumentException.class, () -> {
            factory.getEngine("TYPE_A");
        });
    }
}
