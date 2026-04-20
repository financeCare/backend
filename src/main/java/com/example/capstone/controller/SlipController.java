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
import java.util.UUID;
@RestController
@RequestMapping("/slips")
@RequiredArgsConstructor
public class SlipController {

    private final SlipService slipService;
    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadSlips(@RequestHeader("Authorization") String authorizationHeader, @RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("No files uploaded", HttpStatus.BAD_REQUEST);
        }
        String token = authorizationHeader.replace("Bearer ", "");
        UUID userId = userService.extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            List<?> results = slipService.processSlips(files, user);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace(); // เพิ่ม log ในคอนโซล
            return ResponseEntity.badRequest().body("Processing failed: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Slip>> getMySlips(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        UUID userId = userService.extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Slip> results = slipService.getSlipsByUser(user);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getSlipImage(@PathVariable("id") Long id) {
        Slip slip = slipService.getSlipById(id);
        
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
