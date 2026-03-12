package com.example.capstone.controller;

import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.service.SlipService;
import com.example.capstone.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity<?> uploadSlips(@RequestHeader("Authorization") String authorizationHeader,@RequestParam("files") List<MultipartFile> files) {
        String token = authorizationHeader.replace("Bearer ", "");
        UUID userId = userService.extractUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            List<?> results = slipService.processSlips(files, user);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
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
}
