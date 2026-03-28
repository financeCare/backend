package com.example.capstone.service;

import com.example.capstone.entity.Category;
import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.repository.SlipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SlipServiceTest {

    @Mock
    private MinioService minioService;
    @Mock
    private OcrService ocrService;
    @Mock
    private SlipRepository slipRepository;
    @Mock
    private ReceiverMappingService receiverMappingService;

    @InjectMocks
    private SlipService slipService;

    private User user;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(UUID.randomUUID());
    }

    @Test
    void testProcessSlips_Success() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("slip.jpg");
        List<MultipartFile> files = Collections.singletonList(mockFile);

        when(minioService.uploadFile(mockFile)).thenReturn("2026/03/27/uuid.jpg");

        OcrService.OcrResponse ocrResponse = new OcrService.OcrResponse();
        ocrResponse.setFilename("slip.jpg");
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("bank", "KBank");
        dataNode.put("receiver", "Shop A");
        dataNode.put("amount", "100.00");
        dataNode.put("date", "28 ก.พ. 2569 16:50");
        ocrResponse.setData(dataNode);

        when(ocrService.verifySlips(files)).thenReturn(Collections.singletonList(ocrResponse));
        when(slipRepository.save(any(Slip.class))).thenAnswer(i -> i.getArguments()[0]);
        
        Category suggestedCategory = new Category();
        suggestedCategory.setCategoryName("Food");
        ReceiverMapping mapping = ReceiverMapping.builder().category(suggestedCategory).build();
        when(receiverMappingService.suggestMapping(eq(user.getUserId()), eq("Shop A")))
                .thenReturn(Optional.of(mapping));

        List<Map<String, Object>> results = slipService.processSlips(files, user);

        assertFalse(results.isEmpty());
        Map<String, Object> firstResult = results.get(0);
        Slip savedSlip = (Slip) firstResult.get("slip");
        
        assertEquals("KBank", savedSlip.getSenderBank());
        assertEquals("Shop A", savedSlip.getReceiverName());
        assertEquals("Food", ((Category) firstResult.get("suggestedCategory")).getCategoryName());
        
        verify(minioService).uploadFile(mockFile);
        verify(ocrService).verifySlips(files);
        verify(slipRepository).save(any(Slip.class));
    }

    @Test
    void testGetSlipsByUser() {
        when(slipRepository.findByUserId(user.getUserId())).thenReturn(new ArrayList<>());
        
        List<Slip> results = slipService.getSlipsByUser(user);
        
        assertNotNull(results);
        verify(slipRepository).findByUserId(user.getUserId());
    }
}
