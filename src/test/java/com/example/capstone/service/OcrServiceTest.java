package com.example.capstone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OcrServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OcrService ocrService;

    @Test
    void testVerifySlips_Success() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockFile.getBytes()).thenReturn("fake content".getBytes());

        String mockResponse = "[{\"filename\": \"test.jpg\", \"status\": \"success\", \"method\": \"qr\", \"data\": {}}]";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        JsonNode rootNode = new ObjectMapper().readTree(mockResponse);
        when(objectMapper.readTree(mockResponse)).thenReturn(rootNode);

        List<OcrService.OcrResponse> results = ocrService.verifySlips(Collections.singletonList(mockFile));

        assertFalse(results.isEmpty());
        assertEquals("test.jpg", results.get(0).getFilename());
        assertEquals("success", results.get(0).getStatus());
    }

    @Test
    void testVerifySlips_Error() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getBytes()).thenReturn("fake content".getBytes());
        
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API Down"));

        assertThrows(RuntimeException.class, () -> {
            ocrService.verifySlips(Collections.singletonList(mockFile));
        });
    }
}
