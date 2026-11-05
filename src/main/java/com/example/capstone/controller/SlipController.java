package com.example.capstone.controller;

import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.service.SlipService;
import com.example.capstone.service.UserService;

import com.example.capstone.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/slips")
@RequiredArgsConstructor
@Slf4j
public class SlipController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final SlipService slipService;
    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<List<Map<String, Object>>> uploadSlips(@RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("No files uploaded", HttpStatus.BAD_REQUEST);
        }
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        UUID userId = userService.extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        try {
            List<Map<String, Object>> results = slipService.processSlips(files, user);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Processing slips failed: ", e);
            throw new BusinessException("Processing failed: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Queues the slips for asynchronous OCR and returns immediately (202). Poll
     * {@code GET /slips/{id}/status} (or {@code GET /slips}) for "queued" / "processed" / "failed".
     */
    @PostMapping("/upload-async")
    public ResponseEntity<List<Map<String, Object>>> uploadSlipsAsync(@RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("No files uploaded", HttpStatus.BAD_REQUEST);
        }
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        UUID userId = userService.extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        List<Map<String, Object>> accepted = slipService.enqueueSlips(files, user).stream()
                .map(slip -> Map.<String, Object>of("id", slip.getId(), "status", slip.getStatus()))
                .toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getSlipStatus(@RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long id) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        UUID userId = userService.extractUserIdFromToken(token);
        Slip slip = slipService.getSlipById(id);
        if (!userId.equals(slip.getUserId())) {
            // same answer as for a missing slip, so ids of other users' slips cannot be probed
            throw new BusinessException("Slip not found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Map.of("id", slip.getId(), "status", slip.getStatus()));
    }

    @GetMapping
    public ResponseEntity<List<Slip>> getMySlips(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        UUID userId = userService.extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        List<Slip> results = slipService.getSlipsByUser(user);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getSlipImage(@RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long id) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        UUID userId = userService.extractUserIdFromToken(token);
        Slip slip = slipService.getSlipById(id);
        if (!userId.equals(slip.getUserId())) {
            // same answer as for a missing slip, so ids of other users' slips cannot be probed
            throw new BusinessException("Slip not found", HttpStatus.NOT_FOUND);
        }

        try {
            InputStream inputStream = slipService.getSlipFile(slip.getImagePath());
            String contentType = "image/jpeg"; // Default
            if (slip.getImagePath().toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (slip.getImagePath().toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            log.error("Failed to load slip image {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
