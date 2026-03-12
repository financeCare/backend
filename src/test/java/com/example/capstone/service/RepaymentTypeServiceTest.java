package com.example.capstone.service;

import com.example.capstone.entity.RepaymentType;
import com.example.capstone.repository.RepaymentTypeRepository;
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
public class RepaymentTypeServiceTest {

    @Mock
    private RepaymentTypeRepository repaymentTypeRepository;

    @InjectMocks
    private RepaymentTypeService repaymentTypeService;

    @Test
    void testGetAllRepaymentTypes() {
        when(repaymentTypeRepository.findAll()).thenReturn(List.of(new RepaymentType()));
        List<RepaymentType> result = repaymentTypeService.getAllRepaymentTypes();
        assertFalse(result.isEmpty());
        verify(repaymentTypeRepository).findAll();
    }

    @Test
    void testAddRepaymentType_Success() {
        RepaymentType type = new RepaymentType();
        String result = repaymentTypeService.addRepaymentType(type);
        assertEquals("repaymentType add successfully", result);
        verify(repaymentTypeRepository).save(type);
    }

    @Test
    void testDeleteRepaymentType() {
        Integer id = 1;
        RepaymentType type = new RepaymentType();
        when(repaymentTypeRepository.findById(id)).thenReturn(Optional.of(type));
        
        String result = repaymentTypeService.deleteRepaymentType(id);
        
        assertEquals("repaymentType id " + id + " delete successfully", result);
        verify(repaymentTypeRepository).delete(type);
    }
}
