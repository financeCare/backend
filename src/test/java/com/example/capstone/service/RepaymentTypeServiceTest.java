package com.example.capstone.service;

import com.example.capstone.entity.RepaymentType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.RepaymentTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RepaymentTypeServiceTest {

    @Mock
    private RepaymentTypeRepository repaymentTypeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RepaymentTypeService repaymentTypeService;

    @Test
    void testGetAllRepaymentTypes_CacheHit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("repayments:types")).thenReturn("[{}]");
        
        RepaymentType[] typesArray = new RepaymentType[]{new RepaymentType()};
        when(objectMapper.readValue(any(String.class), eq(RepaymentType[].class))).thenReturn(typesArray);

        List<RepaymentType> result = repaymentTypeService.getAllRepaymentTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        verifyNoInteractions(repaymentTypeRepository);
    }

    @Test
    void testGetAllRepaymentTypes_CacheMiss() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("repayments:types")).thenReturn(null);
        
        List<RepaymentType> repoTypes = List.of(new RepaymentType());
        when(repaymentTypeRepository.findAll()).thenReturn(repoTypes);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        List<RepaymentType> result = repaymentTypeService.getAllRepaymentTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repaymentTypeRepository).findAll();
        verify(valueOperations).set(eq("repayments:types"), any(), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void testGetAllRepaymentTypes_RedisErrorFallback() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis error"));
        
        List<RepaymentType> repoTypes = List.of(new RepaymentType());
        when(repaymentTypeRepository.findAll()).thenReturn(repoTypes);

        List<RepaymentType> result = repaymentTypeService.getAllRepaymentTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repaymentTypeRepository).findAll();
    }

    @Test
    void testAddRepaymentType_Success() {
        RepaymentType type = new RepaymentType();
        String result = repaymentTypeService.addRepaymentType(type);
        
        assertEquals("repaymentType add successfully", result);
        verify(repaymentTypeRepository).save(type);
        verify(redisTemplate).delete("repayments:types");
    }

    @Test
    void testAddRepaymentType_Failure() {
        RepaymentType type = new RepaymentType();
        doThrow(new RuntimeException("Database error")).when(repaymentTypeRepository).save(any());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            repaymentTypeService.addRepaymentType(type);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void testDeleteRepaymentType_Found() {
        Integer id = 1;
        RepaymentType type = new RepaymentType();
        when(repaymentTypeRepository.findById(id)).thenReturn(Optional.of(type));
        
        String result = repaymentTypeService.deleteRepaymentType(id);
        
        assertEquals("repaymentType id 1 delete successfully", result);
        verify(repaymentTypeRepository).delete(type);
        verify(redisTemplate).delete("repayments:types");
    }

    @Test
    void testDeleteRepaymentType_NotFound() {
        Integer id = 1;
        when(repaymentTypeRepository.findById(id)).thenReturn(Optional.empty());
        
        String result = repaymentTypeService.deleteRepaymentType(id);
        
        assertEquals("repaymentType id 1 delete successfully", result);
        verify(repaymentTypeRepository, never()).delete(any());
        verify(redisTemplate).delete("repayments:types");
    }
}
