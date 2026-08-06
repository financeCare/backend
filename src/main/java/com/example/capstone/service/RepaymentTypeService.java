package com.example.capstone.service;

import com.example.capstone.entity.RepaymentType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.RepaymentTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RepaymentTypeService {
    private final RepaymentTypeRepository repaymentTypeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String CACHE_KEY = "repayments:types";

    public List<RepaymentType> getAllRepaymentTypes() {
        try {
            String cachedData = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cachedData != null) {
                return Arrays.asList(objectMapper.readValue(cachedData, RepaymentType[].class));
            }
        } catch (Exception e) {
            // Fallback
        }

        List<RepaymentType> types = repaymentTypeRepository.findAll();
        try {
            String jsonData = objectMapper.writeValueAsString(types);
            redisTemplate.opsForValue().set(CACHE_KEY, jsonData, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            // Fallback
        }
        return types;
    }

    public String addRepaymentType(RepaymentType repaymentType) {
        try {
            repaymentTypeRepository.save(repaymentType);
            evictCache();
        } catch (Exception e) {
            throw new BusinessException(e.getMessage(), HttpStatus.NOT_FOUND);
        }
        return "repaymentType add successfully";
    }

    public String deleteRepaymentType(Integer repaymentTypeId) {
        repaymentTypeRepository.findById(repaymentTypeId).ifPresent(repaymentTypeRepository::delete);
        evictCache();
        return "repaymentType id " + repaymentTypeId + " delete successfully";
    }

    private void evictCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception e) {
            // Fallback
        }
    }
}