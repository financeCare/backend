package com.example.capstone.service;

import com.example.capstone.entity.DebtType;
import com.example.capstone.repository.DebtTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DebtTypeService {
    private final DebtTypeRepository debtTypeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String CACHE_KEY = "debts:types";

    public List<DebtType> getAllDebtTypes() {
        try {
            String cachedData = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cachedData != null) {
                return Arrays.asList(objectMapper.readValue(cachedData, DebtType[].class));
            }
        } catch (Exception e) {
            // Fallback
        }

        List<DebtType> types = debtTypeRepository.findAll();
        try {
            String jsonData = objectMapper.writeValueAsString(types);
            redisTemplate.opsForValue().set(CACHE_KEY, jsonData, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            // Fallback
        }
        return types;
    }

    public String addDebtType(DebtType debtType) {
        debtTypeRepository.save(debtType);
        evictCache();
        return "debtType add successfully";
    }

    public String deleteDebtType(Integer debtId) {
        debtTypeRepository.findById(debtId).ifPresent(debtTypeRepository::delete);
        evictCache();
        return "debtType id " + debtId + " delete successfully";
    }

    private void evictCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception e) {
            // Fallback
        }
    }
}