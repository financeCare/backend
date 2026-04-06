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
        String cacheKey = REDIS_KEY_PREFIX + 
            (request.getKeywords() != null ? request.getKeywords() : "all") + ":" + 
            (request.getLocation() != null ? request.getLocation() : "all") + ":" +
            (request.getCurrentProfession() != null ? request.getCurrentProfession() : "none") + ":" +
            (request.getSkills() != null ? String.join("-", request.getSkills()) : "none");
        
        long startTime = System.currentTimeMillis();

        // 1. Try Cache (Optional)
        try {
            log.info("Checking Jooble cache for key: {}", cacheKey);
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.info("Returning cached Jooble results for key: {}", cacheKey);
                return Arrays.asList(objectMapper.readValue(cachedData, JobSuggestionResponse[].class));
            }
        } catch (Exception e) {
            log.warn("Redis Cache lookup failed (will fetch from API): {}", e.getMessage());
            // Continue even if Redis fails
        }

        // 2. Call Jooble API (Primary)
        try {
            log.info("Calling Jooble API...");
            List<JobSuggestionResponse> jobs = callJoobleApi(request);
            
            if (jobs.isEmpty()) {
                log.warn("Jooble returned empty results for key: {}", cacheKey);
                return jobs; // Return empty list immediately if no jobs found from API
            }

            // 3. Cache results for next time (Optional)
            try {
                String jsonData = objectMapper.writeValueAsString(jobs);
                redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_TTL_DAYS, TimeUnit.DAYS);
                log.info("Result cached. Total time: {}ms", (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                log.warn("Failed to cache results to Redis: {}", e.getMessage());
            }
            
            return jobs;

        } catch (Exception e) {
            log.error("Fatal Jooble Service Error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<JobSuggestionResponse> callJoobleApi(JobSuggestionRequest request) {
        try {
            String url = joobleUrl + apiKey;
            
            // Enhance keywords with profession and skills for better Jooble search results
            StringBuilder keywordsBuilder = new StringBuilder();
            if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
                keywordsBuilder.append(request.getKeywords());
            } else {
                keywordsBuilder.append("part-time");
            }
            
            // Append profession to the search terms
            if (request.getCurrentProfession() != null && !request.getCurrentProfession().isEmpty()) {
                keywordsBuilder.append(" ").append(request.getCurrentProfession());
            }
            
            // Append skills to the search terms
            if (request.getSkills() != null && !request.getSkills().isEmpty()) {
                for (String skill : request.getSkills()) {
                    keywordsBuilder.append(" ").append(skill);
                }
            }

            // Normalize location (prevent emulator "California" from breaking search results in Thailand)
            String location = request.getLocation();
            if (location == null || location.isEmpty() || location.equalsIgnoreCase("California") || location.equalsIgnoreCase("US")) {
                location = "Thailand";
            }

            Map<String, String> body = new HashMap<>();
            body.put("keywords", keywordsBuilder.toString().trim());
            body.put("location", location);

            String requestBodyJson = objectMapper.writeValueAsString(body);
            log.info("Calling Jooble API with body: {}", requestBodyJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBodyJson, headers);
            
            String rawResponse = restTemplate.postForObject(url, entity, String.class);
            log.info("Jooble API Raw Response: {}", rawResponse);

            if (rawResponse == null) {
                return Collections.emptyList();
            }

            JoobleApiResponse response = objectMapper.readValue(rawResponse, JoobleApiResponse.class);
            
            if (response == null || response.getJobs() == null) {
                log.warn("Jooble response is empty or invalid.");
                return Collections.emptyList();
            }

            return response.getJobs().stream()
                    .map(job -> mapToSuggestionResponse(job, request))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to call Jooble API", e);
            return Collections.emptyList();
        }
    }

    private JobSuggestionResponse mapToSuggestionResponse(JoobleJob job, JobSuggestionRequest request) {
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
        
        // Basic Recommendation Logic: 
        // 1. If any skill is mentioned in title/description
        // 2. OR just mark the first 2 results for demonstration
        boolean matchesSkill = false;
        if (request.getSkills() != null) {
            for (String skill : request.getSkills()) {
                if (job.getTitle().toLowerCase().contains(skill.toLowerCase()) || 
                    job.getSnippet().toLowerCase().contains(skill.toLowerCase())) {
                    matchesSkill = true;
                    break;
                }
            }
        }
        
        resp.setRecommended(matchesSkill || job.getId() % 5 == 0); // Mix of match and random for now
        return resp;
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
