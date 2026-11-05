package com.example.capstone.service;

import com.example.capstone.entity.Category;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.ReceiverMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiverMappingServiceTest {

    @Mock
    private ReceiverMappingRepository receiverMappingRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserService userService;
    @Mock
    private com.example.capstone.repository.DebtRepository debtRepository;

    @InjectMocks
    private ReceiverMappingService receiverMappingService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testGetMappingsByUser_Success() {
        ReceiverMapping mapping1 = ReceiverMapping.builder().id(1L).userId(userId).receiverName("Shop A").build();
        ReceiverMapping mapping2 = ReceiverMapping.builder().id(2L).userId(userId).receiverName("Shop B").build();

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(receiverMappingRepository.findByUserId(userId)).thenReturn(Arrays.asList(mapping1, mapping2));

        List<ReceiverMapping> result = receiverMappingService.getMappingsByUser(token);

        assertEquals(2, result.size());
        assertEquals("Shop A", result.get(0).getReceiverName());
        verify(receiverMappingRepository).findByUserId(userId);
    }

    @Test
    void testCreateOrUpdateMapping_NewMapping() {
        String receiverName = "New Shop";
        Integer categoryId = 1;
        Category category = new Category();
        category.setCategoryId(categoryId);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(receiverMappingRepository.findByUserIdAndReceiverName(userId, receiverName)).thenReturn(Optional.empty());
        when(receiverMappingRepository.save(any(ReceiverMapping.class))).thenAnswer(i -> i.getArguments()[0]);

        ReceiverMapping result = receiverMappingService.createOrUpdateMapping(token, receiverName, categoryId, null);

        assertNotNull(result);
        assertEquals(receiverName, result.getReceiverName());
        assertEquals(category, result.getCategory());
        verify(receiverMappingRepository).save(any(ReceiverMapping.class));
    }

    @Test
    void testCreateOrUpdateMapping_UpdateExisting() {
        String receiverName = "Existing Shop";
        Integer categoryId = 2;
        Category newCategory = new Category();
        newCategory.setCategoryId(categoryId);

        ReceiverMapping existingMapping = ReceiverMapping.builder()
                .id(10L)
                .userId(userId)
                .receiverName(receiverName)
                .build();

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(Optional.of(newCategory));
        when(receiverMappingRepository.findByUserIdAndReceiverName(userId, receiverName)).thenReturn(Optional.of(existingMapping));
        when(receiverMappingRepository.save(any(ReceiverMapping.class))).thenAnswer(i -> i.getArguments()[0]);

        ReceiverMapping result = receiverMappingService.createOrUpdateMapping(token, receiverName, categoryId, null);

        assertEquals(newCategory, result.getCategory());
        verify(receiverMappingRepository).save(existingMapping);
    }

    @Test
    void testDeleteMapping_Success() {
        Long mappingId = 1L;
        ReceiverMapping mapping = ReceiverMapping.builder()
                .id(mappingId)
                .userId(userId)
                .build();

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(receiverMappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));

        receiverMappingService.deleteMapping(mappingId, token);

        verify(receiverMappingRepository).delete(mapping);
    }

    @Test
    void testDeleteMapping_OtherUsersMappingIsNotFound() {
        Long mappingId = 1L;
        UUID otherUserId = UUID.randomUUID();
        ReceiverMapping mapping = ReceiverMapping.builder()
                .id(mappingId)
                .userId(otherUserId)
                .build();

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(receiverMappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            receiverMappingService.deleteMapping(mappingId, token);
        });

        // another user's mapping looks exactly like a missing one
        assertEquals("Mapping not found", exception.getMessage());
        verify(receiverMappingRepository, never()).delete(any());
    }

    @Test
    void testSuggestCategory_Success() {
        String receiverName = "Shop A";
        Category category = new Category();
        category.setCategoryName("Food");
        ReceiverMapping mapping = ReceiverMapping.builder().receiverName(receiverName).category(category).build();

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(receiverMappingRepository.findByUserId(userId)).thenReturn(List.of(mapping));

        Optional<Category> result = receiverMappingService.suggestCategory(token, receiverName);

        assertTrue(result.isPresent());
        assertEquals("Food", result.get().getCategoryName());
    }

    @Test
    void testCreateMapping_WithDebtId() {
        String receiverName = "Debt Repayment";
        UUID debtId = UUID.randomUUID();

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserId(debtId, userId)).thenReturn(Optional.of(new com.example.capstone.entity.Debt()));
        when(receiverMappingRepository.findByUserIdAndReceiverName(userId, receiverName)).thenReturn(Optional.empty());
        when(receiverMappingRepository.save(any(ReceiverMapping.class))).thenAnswer(i -> i.getArguments()[0]);

        ReceiverMapping result = receiverMappingService.createOrUpdateMapping(token, receiverName, null, debtId);

        assertNotNull(result);
        assertEquals(debtId, result.getDebtId());
        assertNull(result.getCategory());
    }

    @Test
    void testCreateMapping_CategoryOfAnotherUserIsRejected() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(categoryRepository.findByCategoryIdAndUserId(7, userId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> receiverMappingService.createOrUpdateMapping(token, "Shop", 7, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(receiverMappingRepository, never()).save(any());
    }

    @Test
    void testCreateMapping_DebtOfAnotherUserIsRejected() {
        UUID foreignDebt = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(debtRepository.findByDebtIdAndUserId(foreignDebt, userId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> receiverMappingService.createOrUpdateMapping(token, "Shop", null, foreignDebt));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(receiverMappingRepository, never()).save(any());
    }
}
