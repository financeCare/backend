package com.example.capstone.service;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.messaging.SlipOcrMessage;
import com.example.capstone.messaging.SlipOcrPublisher;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.SlipRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.example.capstone.dto.TransactionRequest;
import java.io.IOException;
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
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_FAILED = "failed";

    private final MinioService minioService;
    private final OcrService ocrService;
    private final SlipRepository slipRepository;
    private final ReceiverMappingService receiverMappingService;
    private final RepaymentService repaymentService;
    private final TransactionService transactionService;
    private final DebtRepository debtRepository;
    private final ObjectProvider<SlipOcrPublisher> slipOcrPublisher;

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
                    .ifPresent(mapping -> processMappingForSlip(mapping, savedSlip, user.getUserId(), result));

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

    private void processMappingForSlip(ReceiverMapping mapping, Slip savedSlip, UUID userId, Map<String, Object> result) {
        if (mapping.getCategory() != null && savedSlip.getAmount() != null) {
            result.put("suggestedCategory", mapping.getCategory());
            tryAutoCreateTransaction(mapping, savedSlip, userId, result);
        }
        if (mapping.getDebtId() != null && savedSlip.getAmount() != null) {
            result.put("suggestedDebtId", mapping.getDebtId());
            tryAutoCreateDebtPayment(mapping, savedSlip, userId, result);
        }
    }

    private void tryAutoCreateTransaction(ReceiverMapping mapping, Slip savedSlip, UUID userId, Map<String, Object> result) {
        try {
            TransactionRequest req = new TransactionRequest();
            req.setCategoryId(mapping.getCategory().getCategoryId());
            req.setAmount(savedSlip.getAmount().doubleValue());
            req.setTransactionDate(savedSlip.getTransferDate() != null ? savedSlip.getTransferDate() : LocalDateTime.now(DEFAULT_ZONE));
            req.setDescription("Auto-created from slip: " + savedSlip.getReceiverName());
            req.setReceiverName(savedSlip.getReceiverName());
            req.setSenderBank(savedSlip.getSenderBank());
            req.setSlipId(savedSlip.getId());
            transactionService.createTransaction(userId, req);
            result.put("autoCreated", true);
            log.info("Auto-created budget transaction for user {} from slip {}", userId, savedSlip.getId());
        } catch (Exception e) {
            log.error("Failed to auto-create budget transaction: {}", e.getMessage());
        }
    }

    private void tryAutoCreateDebtPayment(ReceiverMapping mapping, Slip savedSlip, UUID userId, Map<String, Object> result) {
        try {
            Debt debt = debtRepository.findByDebtIdAndUserId(mapping.getDebtId(), userId).orElse(null);
            if (debt != null) {
                LocalDate paymentDate = savedSlip.getTransferDate() != null ? savedSlip.getTransferDate().toLocalDate() : LocalDate.now(DEFAULT_ZONE);
                repaymentService.processDebtPayment(debt, savedSlip.getAmount(), paymentDate, savedSlip.getId());
                result.put("autoCreated", true);
                log.info("Auto-created debt payment for user {} from slip {}", userId, savedSlip.getId());
            }
        } catch (Exception e) {
            log.error("Failed to auto-create debt payment: {}", e.getMessage());
        }
    }

    /**
     * Asynchronous variant of {@link #processSlips}: stores the images, creates the slips as "queued" and hands
     * them to the OCR pipeline. The caller gets the queued slips immediately; the result is written back by
     * {@link #processQueuedSlip}. Deliberately not transactional: the slip rows must be committed before the
     * message is published, otherwise a fast consumer could look for a row that is not visible yet.
     */
    public List<Slip> enqueueSlips(List<MultipartFile> files, User user) {
        SlipOcrPublisher publisher = slipOcrPublisher.getIfAvailable();
        if (publisher == null) {
            throw new BusinessException("Asynchronous slip processing is not enabled", HttpStatus.SERVICE_UNAVAILABLE);
        }
        ensureLimit(user, files.size());

        List<Slip> queued = new ArrayList<>();
        for (MultipartFile file : files) {
            String imagePath;
            try {
                imagePath = minioService.uploadFile(file);
            } catch (IOException e) {
                throw new BusinessException("Failed to store slip image: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
            Slip slip = slipRepository.save(Slip.builder()
                    .userId(user.getUserId())
                    .imagePath(imagePath)
                    .status(STATUS_QUEUED)
                    .build());
            try {
                publisher.publish(new SlipOcrMessage(slip.getId(), user.getUserId(), file.getOriginalFilename(), file.getContentType()));
            } catch (Exception e) {
                log.error("Could not queue slip {}: {}", slip.getId(), e.getMessage());
                slip.setStatus(STATUS_FAILED);
                slip = slipRepository.save(slip);
            }
            queued.add(slip);
        }
        return queued;
    }

    /**
     * Consumer side of the pipeline. Idempotent (messages are delivered at least once): a slip that is no longer
     * "queued" is skipped, and a slip deleted in the meantime is dropped. Any failure is thrown so the listener
     * retry and the dead-letter queue take over.
     */
    @Transactional
    public void processQueuedSlip(Long slipId) {
        Slip slip = slipRepository.findById(slipId).orElse(null);
        if (slip == null) {
            log.warn("Slip {} no longer exists, dropping its OCR message", slipId);
            return;
        }
        if (!STATUS_QUEUED.equals(slip.getStatus())) {
            log.info("Slip {} is already {}, skipping duplicate message", slipId, slip.getStatus());
            return;
        }

        byte[] image;
        try (InputStream in = minioService.getFile(slip.getImagePath())) {
            image = in.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException("Failed to read slip image: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String path = slip.getImagePath();
        MultipartFile file = new ByteArrayMultipartFile(path.substring(path.lastIndexOf('/') + 1), contentTypeOf(path), image);
        OcrService.OcrResponse response = ocrService.verifySlips(List.of(file)).stream().findFirst()
                .orElseThrow(() -> new BusinessException("OCR returned no result", HttpStatus.BAD_GATEWAY));
        JsonNode data = response.getData();
        if (!"success".equalsIgnoreCase(response.getStatus()) || data == null || data.isMissingNode()) {
            throw new BusinessException("OCR did not succeed (status: " + response.getStatus() + ")", HttpStatus.BAD_GATEWAY);
        }

        slip.setSenderBank(data.path("bank").asText(null));
        slip.setReceiverName(data.path("receiver").asText(null));
        slip.setAmount(parseSafeBigDecimal(data.path("amount").asText(null)));
        slip.setMemo(data.path("memo").asText(null));
        slip.setQrData(data.path("qr_raw").asText(null));
        slip.setRawTexts(data.toString());
        slip.setTransferDate(extractTransferDate(data.path("date").asText(null)));
        slip.setStatus(STATUS_PROCESSED);
        slipRepository.save(slip);

        receiverMappingService.suggestMapping(slip.getUserId(), slip.getReceiverName())
                .ifPresent(mapping -> processMappingForSlip(mapping, slip, slip.getUserId(), new HashMap<>()));
    }

    /** Called for messages that ended up in the dead-letter queue. */
    @Transactional
    public void markFailed(Long slipId) {
        slipRepository.findById(slipId).ifPresent(slip -> {
            if (STATUS_QUEUED.equals(slip.getStatus())) {
                slip.setStatus(STATUS_FAILED);
                slipRepository.save(slip);
            }
        });
    }

    private String contentTypeOf(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
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
