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
    public ReceiverMapping createOrUpdateMapping(String token, String receiverName, Integer categoryId) {
        UUID userId = userService.extractUserIdFromToken(token);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Optional<ReceiverMapping> existing = receiverMappingRepository.findByUserIdAndReceiverName(userId, receiverName);
        
        if (existing.isPresent()) {
            ReceiverMapping mapping = existing.get();
            mapping.setCategory(category);
            return receiverMappingRepository.save(mapping);
        } else {
            ReceiverMapping mapping = ReceiverMapping.builder()
                    .userId(userId)
                    .receiverName(receiverName)
                    .category(category)
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
        if (receiverName == null || receiverName.trim().isEmpty()) {
            return Optional.empty();
        }
        return receiverMappingRepository.findByUserIdAndReceiverName(userId, receiverName)
                .map(ReceiverMapping::getCategory);
    }
}
