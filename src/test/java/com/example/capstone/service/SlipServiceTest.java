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
    @Mock
    private RepaymentService repaymentService;
    @Mock
    private TransactionService transactionService;

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
        verify(transactionService).createTransaction(eq(user.getUserId()), any());
    }

    @Test
    void testProcessSlips_AutoDebtPayment() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("slip.jpg");
        List<MultipartFile> files = Collections.singletonList(mockFile);

        when(minioService.uploadFile(mockFile)).thenReturn("path/to/img.jpg");

        OcrService.OcrResponse ocrResponse = new OcrService.OcrResponse();
        ocrResponse.setFilename("slip.jpg");
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("receiver", "Debt Collector");
        dataNode.put("amount", "5000.00");
        ocrResponse.setData(dataNode);

        when(ocrService.verifySlips(files)).thenReturn(Collections.singletonList(ocrResponse));
        when(slipRepository.save(any(Slip.class))).thenAnswer(i -> i.getArguments()[0]);

        UUID debtId = UUID.randomUUID();
        ReceiverMapping mapping = ReceiverMapping.builder().debtId(debtId).build();
        when(receiverMappingService.suggestMapping(eq(user.getUserId()), eq("Debt Collector")))
                .thenReturn(Optional.of(mapping));

        slipService.processSlips(files, user);

        verify(repaymentService).payDebt(eq(user.getUserId()), any());
    }

    @Test
    void testEnsureLimit_FIFO() throws Exception {
        // Set maxCount to 2 for this test if possible, or assume 50
        // Since maxCount is @Value, it might be 50 in tests unless we use ReflectionTestUtils
        // Let's assume 50 and mock 51 existing slips
        
        List<Slip> existingSlips = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Slip s = new Slip();
            s.setId((long)i);
            s.setImagePath("old/path/" + i);
            existingSlips.add(s);
        }
        
        when(slipRepository.findByUserIdOrderByCreatedAtAsc(user.getUserId())).thenReturn(existingSlips);
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("new.jpg");
        
        when(ocrService.verifySlips(any())).thenReturn(new ArrayList<>()); // Just to skip the rest
        
        slipService.processSlips(Collections.singletonList(mockFile), user);
        
        // Should delete the oldest one (index 0)
        verify(minioService).deleteFile("old/path/0");
        verify(slipRepository).delete(existingSlips.get(0));
    }

    @Test
    void testGetSlipById_Success() {
        Slip slip = new Slip();
        slip.setId(1L);
        when(slipRepository.findById(1L)).thenReturn(Optional.of(slip));
        
        Slip result = slipService.getSlipById(1L);
        
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testGetSlipById_NotFound() {
        when(slipRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> slipService.getSlipById(1L));
    }

    @Test
    void testGetSlipsByUser() {
        when(slipRepository.findByUserId(user.getUserId())).thenReturn(new ArrayList<>());
        
        List<Slip> results = slipService.getSlipsByUser(user);
        
        assertNotNull(results);
        verify(slipRepository).findByUserId(user.getUserId());
    }
}
