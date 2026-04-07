package com.example.capstone.service;

import com.example.capstone.dto.JobSuggestionRequest;
import com.example.capstone.dto.JobSuggestionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
        // 1. Normalize Location early (for consistent cache keys)
        String location = request.getLocation();
        if (location == null || location.isEmpty() || location.equalsIgnoreCase("California") || location.equalsIgnoreCase("US") || location.equalsIgnoreCase("Thailand")) {
            location = ""; // Empty location for th.jooble.org means search all of Thailand
        }

        // 2. Build concise keywords
        String searchKeywords = buildSearchKeywords(request);
        
        String cacheKey = REDIS_KEY_PREFIX + 
            searchKeywords.replace(" ", "-") + ":" + 
            location;
        
        // 3. Try Cache (Optional)
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

        // 4. Call Jooble API (Primary)
        try {
            log.info("Calling Jooble API...");
            List<JobSuggestionResponse> jobs = callJoobleApi(searchKeywords, location, request);
            
            if (jobs.isEmpty()) {
                log.warn("Jooble returned empty results for key: {}", cacheKey);
                return Collections.emptyList(); 
            }

            // 5. Cache results for next time (Optional)
            try {
                String jsonData = objectMapper.writeValueAsString(jobs);
                redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_TTL_DAYS, TimeUnit.DAYS);
                log.info("Result cached in Redis.");
            } catch (Exception e) {
                log.warn("Failed to cache results to Redis: {}", e.getMessage());
            }
            
            return jobs;

        } catch (Exception e) {
            log.error("Jooble API Error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildSearchKeywords(JobSuggestionRequest request) {
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            return request.getKeywords();
        }
        
        StringBuilder sb = new StringBuilder();
        
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            // Add skills to the search terms
            for (String skill : request.getSkills()) {
                if (sb.length() < 30) { // Limit length for Jooble
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(skill);
                }
            }
        }
        
        String searchKeywords = sb.length() > 0 ? sb.toString() : "งานเสริม";
        
        // Append negative keywords to exclude full-time jobs
        return searchKeywords + " -เต็มเวลา -งานประจำ";
    }

    private List<JobSuggestionResponse> callJoobleApi(String searchKeywords, String location, JobSuggestionRequest originalRequest) {
        try {
            String cleanApiKey = apiKey.trim();
            String maskedKey = cleanApiKey.substring(0, 4) + "****" + cleanApiKey.substring(cleanApiKey.length() - 4);
            String url = joobleUrl + cleanApiKey;
            
            log.info("Requesting Jooble API: {} (Masked: {}api/{})", url.replace(cleanApiKey, maskedKey), joobleUrl, maskedKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            Map<String, Object> body = new HashMap<>(); 
            body.put("keywords", searchKeywords);
            body.put("location", location);
            body.put("searchMode", 0); // 0 = Broad match (Mimics web search)

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            log.info("Calling Jooble API with body: {}", objectMapper.writeValueAsString(body));

            ResponseEntity<JoobleApiResponse> response = restTemplate.postForEntity(url, entity, JoobleApiResponse.class);
            
            log.info("Jooble API Response Status: {}", response.getStatusCode());
            
            JoobleApiResponse joobleResponse = response.getBody();
            if (joobleResponse != null) {
                log.info("Jooble API Raw Response: Total={}, Jobs={}", 
                    joobleResponse.getTotalCount(), 
                    joobleResponse.getJobs() != null ? joobleResponse.getJobs().size() : 0);
                
                if (joobleResponse.getJobs() != null) {
                    return joobleResponse.getJobs().stream()
                        .map(job -> mapToSuggestionResponse(job, originalRequest)).collect(Collectors.toList());
                }
            }
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to call Jooble API", e);
            return Collections.emptyList();
        }
    }

    private JobSuggestionResponse mapToSuggestionResponse(JoobleJob job, JobSuggestionRequest request) {
        JobSuggestionResponse resp = new JobSuggestionResponse();
        resp.setId(String.valueOf(job.getId()));
        resp.setTitle(job.getTitle());
        resp.setType(job.getType() != null && !job.getType().isEmpty() ? job.getType() : "งานแนะนำ");
        resp.setEstimatedIncome(calculateDailyIncome(job.getSalary()));
        resp.setDescription(cleanSnippet(job.getSnippet()));
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

    private String cleanSnippet(String snippet) {
        if (snippet == null) return "";
        return snippet.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&#39;", "'")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String calculateDailyIncome(String rawSalary) {
        if (rawSalary == null || rawSalary.isEmpty() || rawSalary.toLowerCase().contains("ตามตกลง")) {
            return "ไม่ระบุ (ตามตกลง)";
        }

        try {
            // Clean up comma and handle range marks
            String cleanSalary = rawSalary.replace(",", "");
            
            // Extract numbers
            List<Double> numbers = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(cleanSalary);
            while (m.find()) {
                numbers.add(Double.parseDouble(m.group()));
            }

            if (numbers.isEmpty()) return rawSalary;

            // Detection logic: Threshold for monthly is 4000
            boolean isMonthly = rawSalary.contains("เดือน") || rawSalary.contains("month") || numbers.get(0) >= 4000;
            boolean isHourly = rawSalary.contains("ชั่วโมง") || rawSalary.contains("hour") || rawSalary.contains("ชม.");
            boolean isDaily = rawSalary.contains("วัน") || rawSalary.contains("day");

            if (isDaily && !isMonthly) return rawSalary; // Already daily

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < numbers.size(); i++) {
                double val = numbers.get(i);
                double daily;
                
                if (isMonthly) {
                    daily = val / 30.0;
                } else if (isHourly) {
                    daily = val * 8.0;
                } else {
                    daily = val; 
                }

                // Round to nice numbers (nearest 5)
                long rounded = Math.round(daily / 5.0) * 5; 
                result.append(rounded);
                if (i < numbers.size() - 1 && numbers.size() > 1) result.append(" - ");
            }

            return result.toString();
        } catch (Exception e) {
            return rawSalary;
        }
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
