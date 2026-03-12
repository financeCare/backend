package com.example.capstone.service;

import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.repository.SlipRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.chrono.ThaiBuddhistDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlipService {

    private final MinioService minioService;
    private final OcrService ocrService;
    private final SlipRepository slipRepository;
    private final ReceiverMappingService receiverMappingService;

    @Transactional
    public List<Map<String, Object>> processSlips(List<MultipartFile> files, User user) {
        // 1. Upload to MinIO
        Map<String, String> filenameToPathMap = new HashMap<>();
        for (MultipartFile file : files) {
            try {
                String path = minioService.uploadFile(file);
                filenameToPathMap.put(file.getOriginalFilename(), path);
            } catch (Exception e) {
                log.error("Failed to upload file {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }

        // 2. Call OCR Service
        List<OcrService.OcrResponse> ocrResults = ocrService.verifySlips(files);

        // 3. Save to DB and Prepare Response with Suggestions
        List<Map<String, Object>> results = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
        // Note: Thai dates like "28 ก.พ. 2569 16:50" might need a custom parser if not handled by standard formatters
        // For simplicity, we'll try to parse or keep as null if failed.

        for (OcrService.OcrResponse ocrResp : ocrResults) {
            String imagePath = filenameToPathMap.get(ocrResp.getFilename());
            if (imagePath == null) continue;

            JsonNode data = ocrResp.getData();
            Slip slip = Slip.builder()
                    .userId(user.getUserId())
                    .senderBank(data.path("bank").asText(null))
                    .receiverName(data.path("receiver").asText(null))
                    .amount(data.has("amount") ? new BigDecimal(data.path("amount").asText()) : null)
                    .memo(data.path("memo").asText(null))
                    .imagePath(imagePath)
                    .qrData(data.path("qr_raw").asText(null))
                    .rawTexts(data.toString()) // Save the whole data node as raw_texts
                    .status("processed")
                    .createdAt(LocalDateTime.now())
                    .build();

            // Handle date parsing (Thai month names: 28 ก.พ. 2569 16:50)
            try {
                String dateStr = data.path("date").asText(null);
                if (dateStr != null) {
                    slip.setTransferDate(parseThaiDate(dateStr));
                }
            } catch (Exception e) {
                log.warn("Failed to parse date '{}' for slip: {}", data.path("date").asText(), e.getMessage());
            }

            Slip savedSlip = slipRepository.save(slip);
            
            // 4. Look up for suggested category
            Map<String, Object> result = new HashMap<>();
            result.put("slip", savedSlip);
            
            String receiverName = data.path("receiver").asText(null);
            receiverMappingService.suggestCategory(user.getUserId(), receiverName).ifPresent(category -> {
                result.put("suggestedCategory", category);
            });
            
            results.add(result);
        }

        return results;
    }

    public List<Slip> getSlipsByUser(User user) {
        return slipRepository.findByUserId(user.getUserId());
    }

    private LocalDateTime parseThaiDate(String thaiDateStr) {
        try {
            // Example: "28 ก.พ. 2569 16:50"
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern("d MMM ")
                    .appendPattern("yyyy")
                    .appendPattern(" HH:mm")
                    .toFormatter(new Locale("th", "TH"));
            
            return LocalDateTime.parse(thaiDateStr, formatter);
        } catch (Exception e) {
            log.warn("Thai date parsing failed for '{}': {}", thaiDateStr, e.getMessage());
            return null;
        }
    }
}
