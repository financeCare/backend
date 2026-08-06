package com.example.capstone.service;

import com.example.capstone.entity.DebtType;
import com.example.capstone.repository.DebtTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DebtTypeServiceTest {

    @Mock
    private DebtTypeRepository debtTypeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DebtTypeService debtTypeService;

    @Test
    void testGetAllDebtTypes_CacheHit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("debts:types")).thenReturn("[{}]");
        
        DebtType[] typesArray = new DebtType[]{new DebtType()};
        when(objectMapper.readValue(any(String.class), eq(DebtType[].class))).thenReturn(typesArray);

        List<DebtType> result = debtTypeService.getAllDebtTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        verifyNoInteractions(debtTypeRepository);
    }

    @Test
    void testGetAllDebtTypes_CacheMiss() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("debts:types")).thenReturn(null);
        
        List<DebtType> repoTypes = List.of(new DebtType());
        when(debtTypeRepository.findAll()).thenReturn(repoTypes);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        List<DebtType> result = debtTypeService.getAllDebtTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(debtTypeRepository).findAll();
        verify(valueOperations).set(eq("debts:types"), any(), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void testGetAllDebtTypes_RedisErrorFallback() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis error"));
        
        List<DebtType> repoTypes = List.of(new DebtType());
        when(debtTypeRepository.findAll()).thenReturn(repoTypes);

        List<DebtType> result = debtTypeService.getAllDebtTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(debtTypeRepository).findAll();
    }

    @Test
    void testAddDebtType() {
        DebtType debtType = new DebtType();
        String result = debtTypeService.addDebtType(debtType);
        
        assertEquals("debtType add successfully", result);
        verify(debtTypeRepository).save(debtType);
        verify(redisTemplate).delete("debts:types");
    }

    @Test
    void testDeleteDebtType_Found() {
        Integer debtId = 1;
        DebtType debtType = new DebtType();
        when(debtTypeRepository.findById(debtId)).thenReturn(Optional.of(debtType));
        
        String result = debtTypeService.deleteDebtType(debtId);
        
        assertEquals("debtType id 1 delete successfully", result);
        verify(debtTypeRepository).delete(debtType);
        verify(redisTemplate).delete("debts:types");
    }

    @Test
    void testDeleteDebtType_NotFound() {
        Integer debtId = 1;
        when(debtTypeRepository.findById(debtId)).thenReturn(Optional.empty());
        
        String result = debtTypeService.deleteDebtType(debtId);
        
        assertEquals("debtType id 1 delete successfully", result);
        verify(debtTypeRepository, never()).delete(any());
        verify(redisTemplate).delete("debts:types");
    }
}
