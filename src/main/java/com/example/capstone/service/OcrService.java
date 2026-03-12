package com.example.capstone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OcrService {

    private final String ocrUrl = "http://10.4.88.37:8000/slip/verify";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<OcrResponse> verifySlips(List<MultipartFile> files) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile file : files) {
                body.add("files", new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                });
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String responseStr = restTemplate.postForObject(ocrUrl, requestEntity, String.class);
            log.info("OCR Response: {}", responseStr);

            JsonNode rootNode = objectMapper.readTree(responseStr);
            List<OcrResponse> results = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    OcrResponse resp = new OcrResponse();
                    resp.setFilename(node.path("filename").asText());
                    resp.setStatus(node.path("status").asText());
                    resp.setMethod(node.path("method").asText());
                    resp.setData(node.path("data"));
                    results.add(resp);
                }
            }
            return results;

        } catch (Exception e) {
            log.error("Error calling OCR Service: {}", e.getMessage());
            throw new RuntimeException("OCR Service failed: " + e.getMessage());
        }
    }

    @Getter
    @Setter
    public static class OcrResponse {
        private String filename;
        private String status;
        private String method;
        private JsonNode data;
    }
}
