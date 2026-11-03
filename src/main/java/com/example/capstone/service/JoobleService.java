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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoobleService {

    @Value("${jooble.api.key:}")
    private String apiKey;

    @Value("${jooble.api.url:https://jooble.org/api/}")
    private String joobleUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PREFIX = "jooble:jobs:";
    private static final long CACHE_TTL_HOURS = 6;

    public List<JobSuggestionResponse> getSuggestedJobs(JobSuggestionRequest request) {
        String location = request.getLocation();
        if (location == null || location.isEmpty() || location.equalsIgnoreCase("California") || location.equalsIgnoreCase("US") || location.equalsIgnoreCase("Thailand")) {
            location = "";
        }

        String searchKeywords = buildSearchKeywords(request);
        String cacheKey = REDIS_KEY_PREFIX + searchKeywords.replace(" ", "-") + ":" + location;
        
        Optional<List<JobSuggestionResponse>> cached = getFromCache(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            log.info("Calling Jooble API...");
            List<JobSuggestionResponse> jobs = callJoobleApi(searchKeywords, location, request);
            
            if (jobs.isEmpty()) {
                log.warn("Jooble returned empty results for key: {}", cacheKey);
                return Collections.emptyList(); 
            }

            cacheResults(cacheKey, jobs);
            return jobs;

        } catch (Exception e) {
            log.error("Jooble API Error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Optional<List<JobSuggestionResponse>> getFromCache(String cacheKey) {
        try {
            log.info("Checking Jooble cache for key: {}", cacheKey);
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.info("Returning cached Jooble results for key: {}", cacheKey);
                return Optional.of(Arrays.asList(objectMapper.readValue(cachedData, JobSuggestionResponse[].class)));
            }
        } catch (Exception e) {
            log.warn("Redis Cache lookup failed (will fetch from API): {}", e.getMessage());
        }
        return Optional.empty();
    }

    private void cacheResults(String cacheKey, List<JobSuggestionResponse> jobs) {
        try {
            String jsonData = objectMapper.writeValueAsString(jobs);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.info("Result cached in Redis.");
        } catch (Exception e) {
            log.warn("Failed to cache results to Redis: {}", e.getMessage());
        }
    }

    private String buildSearchKeywords(JobSuggestionRequest request) {
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            return request.getKeywords();
        }
        
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            return String.join(" ", request.getSkills());
        }

        return "งานพาร์ทไทม์ ฟรีแลนซ์";
    }

    private List<JobSuggestionResponse> callJoobleApi(String keywords, String location, JobSuggestionRequest request) {
        String url = joobleUrl + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("keywords", keywords);
        if (location != null && !location.isEmpty()) {
            reqBody.put("location", location);
        }
        reqBody.put("page", 1);
        reqBody.put("result_on_page", 15);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);

        JoobleApiResponse response = restTemplate.postForObject(url, entity, JoobleApiResponse.class);
        if (response != null && response.getJobs() != null) {
            return response.getJobs().stream()
                    .map(job -> mapToSuggestionResponse(job, request))
                    .filter(Objects::nonNull)
                    .toList();
        }

        return Collections.emptyList();
    }

    private JobSuggestionResponse mapToSuggestionResponse(JoobleJob job, JobSuggestionRequest request) {
        JobSuggestionResponse resp = new JobSuggestionResponse();
        resp.setId(String.valueOf(job.getId()));
        resp.setTitle(sanitizeHtml(job.getTitle()));
        resp.setType(job.getType() != null && !job.getType().isEmpty() ? job.getType() : "งานแนะนำ");
        resp.setEstimatedIncome(formatDailySalary(job.getSalary()));
        resp.setDescription(sanitizeHtml(job.getSnippet()));
        resp.setRequirement("สมัครผ่านแพลตฟอร์มต้นทาง");

        Map<String, String> links = new HashMap<>();
        links.put("Jooble", job.getLink());
        resp.setPlatformLinks(links);

        boolean matchesSkill = false;
        if (request.getSkills() != null) {
            for (String skill : request.getSkills()) {
                if ((job.getTitle() != null && job.getTitle().toLowerCase().contains(skill.toLowerCase())) ||
                    (job.getSnippet() != null && job.getSnippet().toLowerCase().contains(skill.toLowerCase()))) {
                    matchesSkill = true;
                    break;
                }
            }
        }
        resp.setRecommended(matchesSkill);
        return resp;
    }

    private String sanitizeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatDailySalary(String rawSalary) {
        if (rawSalary == null || rawSalary.trim().isEmpty()) {
            return "ตามตกลง";
        }

        try {
            List<Double> numbers = new ArrayList<>();
            Pattern p = Pattern.compile("\\b(\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?\\b");
            Matcher m = p.matcher(rawSalary);
            while (m.find()) {
                String numStr = m.group(1).replace(",", "");
                numbers.add(Double.parseDouble(numStr));
            }

            if (numbers.isEmpty()) {
                return rawSalary;
            }

            boolean isMonthly = rawSalary.contains("เดือน") || rawSalary.contains("month") || rawSalary.contains("mth") || rawSalary.contains("ด.") || numbers.get(0) >= 4000;
            boolean isHourly = rawSalary.contains("ชม.") || rawSalary.contains("hour") || rawSalary.contains("hr");
            boolean isDaily = rawSalary.contains("วัน") || rawSalary.contains("day");

            if (isDaily && !isMonthly) {
                return rawSalary;
            }

            return formatCalculatedNumbers(numbers, isMonthly, isHourly);
        } catch (Exception e) {
            return rawSalary;
        }
    }

    private String formatCalculatedNumbers(List<Double> numbers, boolean isMonthly, boolean isHourly) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            double val = numbers.get(i);
            double daily = calculateDailyRate(val, isMonthly, isHourly);
            long rounded = Math.round(daily / 5.0) * 5;
            result.append(rounded);
            if (i < numbers.size() - 1 && numbers.size() > 1) {
                result.append(" - ");
            }
        }
        return result.toString();
    }

    private double calculateDailyRate(double val, boolean isMonthly, boolean isHourly) {
        if (isMonthly) {
            return val / 30.0;
        }
        if (isHourly) {
            return val * 8.0;
        }
        return val;
    }

    @Data
    private static class JoobleApiResponse {
        @JsonProperty("totalCount")
        private Integer totalCount;

        @JsonProperty("jobs")
        private List<JoobleJob> jobs;
    }

    @Data
    private static class JoobleJob {
        private String id;
        private String title;
        private String location;
        private String snippet;
        private String salary;
        private String source;
        private String type;
        private String link;
        private String company;
        private String updated;
    }
}
