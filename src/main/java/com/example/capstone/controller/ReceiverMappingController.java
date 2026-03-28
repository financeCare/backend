package com.example.capstone.controller;

import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.service.ReceiverMappingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/receiver-mappings")
@RequiredArgsConstructor
public class ReceiverMappingController {

    private final ReceiverMappingService receiverMappingService;

    @GetMapping
    public ResponseEntity<List<ReceiverMapping>> getMyMappings(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(receiverMappingService.getMappingsByUser(token));
    }

    @PostMapping
    public ResponseEntity<ReceiverMapping> saveMapping(@RequestHeader("Authorization") String authorizationHeader,@RequestBody MappingRequest request) {
        String token = authorizationHeader.replace("Bearer ", "");
        ReceiverMapping mapping = receiverMappingService.createOrUpdateMapping(
                token, 
                request.getReceiverName(), 
                request.getCategoryId(),
                request.getDebtId()
        );
        return ResponseEntity.ok(mapping);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMapping(@RequestHeader("Authorization") String authorizationHeader,@PathVariable("id") Long id) {
        String token = authorizationHeader.replace("Bearer ", "");
        receiverMappingService.deleteMapping(id, token);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class MappingRequest {
        private String receiverName;
        private Integer categoryId;
        private java.util.UUID debtId;
    }
}
