package com.example.capstone.service;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.SlipRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.example.capstone.dto.TransactionRequest;
import java.io.InputStream;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlipService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Bangkok");

    private final MinioService minioService;
    private final OcrService ocrService;
    private final SlipRepository slipRepository;
    private final ReceiverMappingService receiverMappingService;
    private final RepaymentService repaymentService;
    private final TransactionService transactionService;
    private final DebtRepository debtRepository;

    @Value("${app.slip.max-count:50}")
    private int maxCount;

    @Transactional
    public List<Map<String, Object>> processSlips(List<MultipartFile> files, User user) {
        ensureLimit(user, files.size());
        Map<String, String> filenameToPathMap = uploadFilesToStorage(files);
        List<OcrService.OcrResponse> ocrResults = ocrService.verifySlips(files);

        List<Map<String, Object>> results = new ArrayList<>();
        for (OcrService.OcrResponse ocrResp : ocrResults) {
            processSingleOcrResponse(ocrResp, filenameToPathMap, user).ifPresent(results::add);
        }

        return results;
    }

    private Map<String, String> uploadFilesToStorage(List<MultipartFile> files) {
        Map<String, String> filenameToPathMap = new HashMap<>();
        for (MultipartFile file : files) {
            try {
                String path = minioService.uploadFile(file);
                filenameToPathMap.put(file.getOriginalFilename(), path);
            } catch (Exception e) {
                log.error("Failed to upload file {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        return filenameToPathMap;
    }

    private Optional<Map<String, Object>> processSingleOcrResponse(
            OcrService.OcrResponse ocrResp,
            Map<String, String> filenameToPathMap,
            User user) {
        if (!"success".equalsIgnoreCase(ocrResp.getStatus())) {
            log.warn("Skipping slip {} due to OCR error status: {}", ocrResp.getFilename(), ocrResp.getStatus());
            return Optional.empty();
        }

        String imagePath = filenameToPathMap.get(ocrResp.getFilename());
        JsonNode data = ocrResp.getData();
        if (imagePath == null || data == null || data.isMissingNode()) {
            log.warn("Missing image path or OCR data for file: {}", ocrResp.getFilename());
            return Optional.empty();
        }

        try {
            Slip slip = buildSlipFromData(data, imagePath, user.getUserId());
            Slip savedSlip = slipRepository.save(slip);

            Map<String, Object> result = new HashMap<>();
            result.put("slip", savedSlip);

            String receiverName = data.path("receiver").asText(null);
            receiverMappingService.suggestMapping(user.getUserId(), receiverName)
                    .ifPresent(mapping -> processMappingForSlip(mapping, savedSlip, user, result));

            return Optional.of(result);
        } catch (Exception e) {
            log.error("Failed to process individual slip {}: {}", ocrResp.getFilename(), e.getMessage());
            return Optional.empty();
        }
    }

    private Slip buildSlipFromData(JsonNode data, String imagePath, UUID userId) {
        LocalDateTime transferDate = extractTransferDate(data.path("date").asText(null));
        return Slip.builder()
                .userId(userId)
                .senderBank(data.path("bank").asText(null))
                .receiverName(data.path("receiver").asText(null))
                .amount(parseSafeBigDecimal(data.path("amount").asText(null)))
                .memo(data.path("memo").asText(null))
                .imagePath(imagePath)
                .qrData(data.path("qr_raw").asText(null))
                .rawTexts(data.toString())
                .status("processed")
                .transferDate(transferDate)
                .createdAt(LocalDateTime.now(DEFAULT_ZONE))
                .build();
    }

    private LocalDateTime extractTransferDate(String dateStr) {
        if (dateStr != null && !dateStr.isBlank()) {
            return parseThaiDate(dateStr);
        }
        return null;
    }

    private void processMappingForSlip(ReceiverMapping mapping, Slip savedSlip, User user, Map<String, Object> result) {
        if (mapping.getCategory() != null && savedSlip.getAmount() != null) {
            result.put("suggestedCategory", mapping.getCategory());
            tryAutoCreateTransaction(mapping, savedSlip, user, result);
        }
        if (mapping.getDebtId() != null && savedSlip.getAmount() != null) {
            result.put("suggestedDebtId", mapping.getDebtId());
            tryAutoCreateDebtPayment(mapping, savedSlip, user, result);
        }
    }

    private void tryAutoCreateTransaction(ReceiverMapping mapping, Slip savedSlip, User user, Map<String, Object> result) {
        try {
            TransactionRequest req = new TransactionRequest();
            req.setCategoryId(mapping.getCategory().getCategoryId());
            req.setAmount(savedSlip.getAmount().doubleValue());
            req.setTransactionDate(savedSlip.getTransferDate() != null ? savedSlip.getTransferDate() : LocalDateTime.now(DEFAULT_ZONE));
            req.setDescription("Auto-created from slip: " + savedSlip.getReceiverName());
            req.setReceiverName(savedSlip.getReceiverName());
            req.setSenderBank(savedSlip.getSenderBank());
            req.setSlipId(savedSlip.getId());
            transactionService.createTransaction(user.getUserId(), req);
            result.put("autoCreated", true);
            log.info("Auto-created budget transaction for user {} from slip {}", user.getUserId(), savedSlip.getId());
        } catch (Exception e) {
            log.error("Failed to auto-create budget transaction: {}", e.getMessage());
        }
    }

    private void tryAutoCreateDebtPayment(ReceiverMapping mapping, Slip savedSlip, User user, Map<String, Object> result) {
        try {
            Debt debt = debtRepository.findByDebtIdAndUserId(mapping.getDebtId(), user.getUserId()).orElse(null);
            if (debt != null) {
                LocalDate paymentDate = savedSlip.getTransferDate() != null ? savedSlip.getTransferDate().toLocalDate() : LocalDate.now(DEFAULT_ZONE);
                repaymentService.processDebtPayment(debt, savedSlip.getAmount(), paymentDate, savedSlip.getId());
                result.put("autoCreated", true);
                log.info("Auto-created debt payment for user {} from slip {}", user.getUserId(), savedSlip.getId());
            }
        } catch (Exception e) {
            log.error("Failed to auto-create debt payment: {}", e.getMessage());
        }
    }

    public List<Slip> getSlipsByUser(User user) {
        return slipRepository.findByUserId(user.getUserId());
    }

    public Slip getSlipById(Long id) {
        return slipRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Slip not found", HttpStatus.NOT_FOUND));
    }

    public InputStream getSlipFile(String path) {
        return minioService.getFile(path);
    }

    private BigDecimal parseSafeBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            log.warn("Failed to parse BigDecimal from value '{}': {}", value, e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseThaiDate(String thaiDateStr) {
        try {
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

    private void ensureLimit(User user, int incomingCount) {
        List<Slip> existingSlips = slipRepository.findByUserIdOrderByCreatedAtAsc(user.getUserId());
        int currentCount = existingSlips.size();
        int totalProjected = currentCount + incomingCount;

        if (totalProjected > maxCount) {
            int itemsToDelete = totalProjected - maxCount;
            log.info("User {} has {} slips, uploading {}. Max is {}. Deleting {} oldest slips.",
                    user.getUserId(), currentCount, incomingCount, maxCount, itemsToDelete);

            for (int i = 0; i < Math.min(itemsToDelete, currentCount); i++) {
                Slip slipToDelete = existingSlips.get(i);
                try {
                    minioService.deleteFile(slipToDelete.getImagePath());
                    slipRepository.delete(slipToDelete);
                } catch (Exception e) {
                    log.error("Failed to delete old slip {}: {}", slipToDelete.getId(), e.getMessage());
                }
            }
        }
    }
}
