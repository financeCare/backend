package com.example.capstone.service;

import com.example.capstone.dto.JobSuggestionRequest;
import com.example.capstone.dto.JobSuggestionResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoobleService {

    @Value("${app.jooble.api-key}")
    private String apiKey;

    @Value("${app.jooble.url}")
    private String joobleUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PREFIX = "jooble:jobs:";
    private static final long CACHE_TTL_DAYS = 7;

    public List<JobSuggestionResponse> getSuggestedJobs(JobSuggestionRequest request) {
        String cacheKey = REDIS_KEY_PREFIX + request.getKeywords() + ":" + request.getLocation();
        
        try {
            // 1. Try Cache
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.info("Returning cached Jooble results for key: {}", cacheKey);
                return Arrays.asList(objectMapper.readValue(cachedData, JobSuggestionResponse[].class));
            }

            // 2. Call Jooble API
            List<JobSuggestionResponse> jobs = callJoobleApi(request);
            
            // 3. Fallback handle
            if (jobs.isEmpty()) {
                log.warn("Jooble returned empty results, using fallback mock data.");
                jobs = getMockFallbackJobs(request);
            }

            // 4. Cache results
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(jobs), CACHE_TTL_DAYS, TimeUnit.DAYS);
            
            return jobs;

        } catch (Exception e) {
            log.error("Jooble API Error: {}", e.getMessage());
            return getMockFallbackJobs(request);
        }
    }

    private List<JobSuggestionResponse> callJoobleApi(JobSuggestionRequest request) {
        try {
            String url = joobleUrl + apiKey;
            
            Map<String, String> body = new HashMap<>();
            body.put("keywords", request.getKeywords() != null ? request.getKeywords() : "part-time");
            body.put("location", request.getLocation() != null ? request.getLocation() : "ประเทศไทย");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            JoobleApiResponse response = restTemplate.postForObject(url, entity, JoobleApiResponse.class);
            
            if (response == null || response.getJobs() == null) {
                return Collections.emptyList();
            }

            return response.getJobs().stream()
                    .map(this::mapToSuggestionResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to call Jooble API", e);
            return Collections.emptyList();
        }
    }

    private JobSuggestionResponse mapToSuggestionResponse(JoobleJob job) {
        JobSuggestionResponse resp = new JobSuggestionResponse();
        resp.setId(String.valueOf(job.getId()));
        resp.setTitle(job.getTitle());
        resp.setType(job.getType() != null ? job.getType() : "งานแนะนำ");
        resp.setEstimatedIncome(job.getSalary() != null ? job.getSalary() : "ตามตกลง");
        resp.setDescription(job.getSnippet() != null ? job.getSnippet().replaceAll("<[^>]*>", "") : "");
        resp.setRequirement("สมัครผ่านแพลตฟอร์มต้นทาง");
        
        Map<String, String> links = new HashMap<>();
        links.put("Jooble", job.getLink());
        resp.setPlatformLinks(links);
        resp.setRecommended(false);
        return resp;
    }

    private List<JobSuggestionResponse> getMockFallbackJobs(JobSuggestionRequest request) {
        List<JobSuggestionResponse> fallbacks = new ArrayList<>();
        
        JobSuggestionResponse job1 = new JobSuggestionResponse();
        job1.setId("mock-1");
        job1.setTitle("พนักงานส่งอาหาร (Delivery Rider)");
        job1.setType("รายได้เสริม");
        job1.setEstimatedIncome("500 - 1,000 บาท/วัน");
        job1.setDescription("ขับรถส่งอาหารกับแพลตฟอร์มชั้นนำ เหมาะสำหรับคนมีมอเตอร์ไซค์ส่วนตัว");
        job1.setRequirement("มีใบขับขี่, รถจักรยานยนต์");
        job1.setPlatformLinks(Map.of("Grab", "https://grab.com", "Foodpanda", "https://foodpanda.com"));
        job1.setRecommended(true);

        JobSuggestionResponse job2 = new JobSuggestionResponse();
        job2.setId("mock-2");
        job2.setTitle("รับจ้างคีย์ข้อมูล (Data Entry)");
        job2.setType("Work from Home");
        job2.setEstimatedIncome("300 - 600 บาท/วัน");
        job2.setDescription("งานพิมพ์เอกสาร คีย์ข้อมูลเข้าสู่ระบบ สามารถทำที่บ้านได้");
        job2.setRequirement("คอมพิวเตอร์, อินเทอร์เน็ต");
        job2.setPlatformLinks(Map.of("Fastwork", "https://fastwork.co"));
        
        fallbacks.add(job1);
        fallbacks.add(job2);
        
        return fallbacks;
    }

    @Data
    private static class JoobleApiResponse {
        private List<JoobleJob> jobs;
        private int totalCount;
    }

    @Data
    private static class JoobleJob {
        private String title;
        private String location;
        private String snippet;
        private String salary;
        private String source;
        private String type;
        private String link;
        private String company;
        private String updated;
        private long id;
    }
}
