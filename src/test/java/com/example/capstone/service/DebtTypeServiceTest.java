package com.example.capstone.service;

import com.example.capstone.entity.DebtType;
import com.example.capstone.repository.DebtTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DebtTypeServiceTest {

    @Mock
    private DebtTypeRepository debtTypeRepository;

    @InjectMocks
    private DebtTypeService debtTypeService;

    @Test
    void testGetAllDebtTypes() {
        when(debtTypeRepository.findAll()).thenReturn(List.of(new DebtType()));
        List<DebtType> result = debtTypeService.getAllDebtTypes();
        assertFalse(result.isEmpty());
        verify(debtTypeRepository).findAll();
    }

    @Test
    void testAddDebtType() {
        DebtType debtType = new DebtType();
        String result = debtTypeService.addDebtType(debtType);
        assertEquals("debtType add successfully", result);
        verify(debtTypeRepository).save(debtType);
    }

    @Test
    void testDeleteDebtType() {
        Integer debtId = 1;
        DebtType debtType = new DebtType();
        when(debtTypeRepository.findById(debtId)).thenReturn(Optional.of(debtType));
        
        String result = debtTypeService.deleteDebtType(debtId);
        
        assertEquals("debtType id " + debtId + " delete successfully", result);
        verify(debtTypeRepository).delete(debtType);
    }
}
