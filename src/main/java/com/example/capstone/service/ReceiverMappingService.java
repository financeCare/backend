package com.example.capstone.service;

import com.example.capstone.entity.Category;
import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.entity.User;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.ReceiverMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiverMappingService {

    private final ReceiverMappingRepository receiverMappingRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;

    public List<ReceiverMapping> getMappingsByUser(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        return receiverMappingRepository.findByUserId(userId);
    }

    @Transactional
    public ReceiverMapping createOrUpdateMapping(String token, String receiverName, Integer categoryId, UUID debtId) {
        UUID userId = userService.extractUserIdFromToken(token);
        
        Category category = null;
        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
        }

        Optional<ReceiverMapping> existing = receiverMappingRepository.findByUserIdAndReceiverName(userId, receiverName);
        
        if (existing.isPresent()) {
            ReceiverMapping mapping = existing.get();
            mapping.setCategory(category);
            mapping.setDebtId(debtId);
            return receiverMappingRepository.save(mapping);
        } else {
            ReceiverMapping mapping = ReceiverMapping.builder()
                    .userId(userId)
                    .receiverName(receiverName)
                    .category(category)
                    .debtId(debtId)
                    .build();
            return receiverMappingRepository.save(mapping);
        }
    }

    @Transactional
    public void deleteMapping(Long id, String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        ReceiverMapping mapping = receiverMappingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mapping not found"));
        
        if (!mapping.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this mapping");
        }
        
        receiverMappingRepository.delete(mapping);
    }

    public Optional<Category> suggestCategory(String token, String receiverName) {
        UUID userId = userService.extractUserIdFromToken(token);
        return suggestCategory(userId, receiverName);
    }

    public Optional<Category> suggestCategory(UUID userId, String receiverName) {
        return suggestMapping(userId, receiverName)
                .map(ReceiverMapping::getCategory);
    }

    public Optional<ReceiverMapping> suggestMapping(UUID userId, String receiverName) {
        if (receiverName == null || receiverName.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String normalizedReceiver = receiverName.toLowerCase().trim();
        List<ReceiverMapping> allMappings = receiverMappingRepository.findByUserId(userId);
        
        // Try exact match first for better precision
        Optional<ReceiverMapping> exactMatch = allMappings.stream()
                .filter(m -> m.getReceiverName().equalsIgnoreCase(receiverName.trim()))
                .findFirst();
        
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        // Fuzzy match: check if OCR name contains mapping name OR mapping name contains OCR name
        return allMappings.stream()
                .filter(m -> {
                    String mappingName = m.getReceiverName().toLowerCase().trim();
                    return normalizedReceiver.contains(mappingName) || mappingName.contains(normalizedReceiver);
                })
                .findFirst();
    }
}
