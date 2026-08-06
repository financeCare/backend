package com.example.capstone.service;

import com.example.capstone.dto.OccupationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OccupationDiscoveryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OccupationDiscoveryService occupationDiscoveryService;

    @Test
    void testGetRecommendedOccupations_CacheHit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("occupations:recommended")).thenReturn("[{\"title\":\"Rider\"}]");
        
        OccupationResponse[] responseArray = new OccupationResponse[]{
                OccupationResponse.builder().title("Rider").build()
        };
        when(objectMapper.readValue(any(String.class), eq(OccupationResponse[].class))).thenReturn(responseArray);

        List<OccupationResponse> result = occupationDiscoveryService.getRecommendedOccupations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Rider", result.get(0).getTitle());
    }

    @Test
    void testGetRecommendedOccupations_CacheMiss() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("occupations:recommended")).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        List<OccupationResponse> result = occupationDiscoveryService.getRecommendedOccupations();

        assertNotNull(result);
        assertTrue(result.size() > 0);
        assertEquals("พนักงานส่งอาหาร (Rider)", result.get(0).getTitle());

        verify(valueOperations).set(eq("occupations:recommended"), any(), eq(30L), eq(TimeUnit.DAYS));
    }

    @Test
    void testGetRecommendedOccupations_RedisErrorFallback() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        List<OccupationResponse> result = occupationDiscoveryService.getRecommendedOccupations();

        assertNotNull(result);
        assertTrue(result.size() > 0);
        assertEquals("พนักงานส่งอาหาร (Rider)", result.get(0).getTitle());
    }
}
