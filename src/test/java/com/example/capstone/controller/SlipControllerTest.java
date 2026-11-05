package com.example.capstone.controller;

import com.example.capstone.entity.User;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.service.SlipService;
import com.example.capstone.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlipController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SlipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlipService slipService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void testUploadSlips_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserId(userId);

        MockMultipartFile file = new MockMultipartFile("files", "slip.jpg", MediaType.IMAGE_JPEG_VALUE, "test image content".getBytes());

        when(userService.extractUserIdFromToken(anyString())).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(slipService.processSlips(anyList(), eq(user))).thenReturn(new ArrayList<>());

        mockMvc.perform(multipart("/slips/upload")
                        .file(file)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk());

        verify(slipService).processSlips(anyList(), eq(user));
    }

    @Test
    void testGetMySlips_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserId(userId);

        when(userService.extractUserIdFromToken(anyString())).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(slipService.getSlipsByUser(user)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/slips")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk());

        verify(slipService).getSlipsByUser(user);
    }

    @Test
    void testSlipImage_OwnerGetsTheImage() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        com.example.capstone.entity.Slip slip = new com.example.capstone.entity.Slip();
        slip.setId(5L);
        slip.setUserId(userId);
        slip.setImagePath("2026/11/05/a.png");
        org.mockito.Mockito.when(userService.extractUserIdFromToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(userId);
        org.mockito.Mockito.when(slipService.getSlipById(5L)).thenReturn(slip);
        org.mockito.Mockito.when(slipService.getSlipFile("2026/11/05/a.png"))
                .thenReturn(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/slips/5/image")
                        .header("Authorization", "Bearer token"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));
    }

    @Test
    void testSlipImage_AnotherUsersSlipIsNotFoundAndTheFileIsNeverRead() throws Exception {
        com.example.capstone.entity.Slip slip = new com.example.capstone.entity.Slip();
        slip.setId(6L);
        slip.setUserId(java.util.UUID.randomUUID());   // belongs to user A
        slip.setImagePath("2026/11/05/secret.png");
        org.mockito.Mockito.when(userService.extractUserIdFromToken(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.UUID.randomUUID());   // the caller is user B
        org.mockito.Mockito.when(slipService.getSlipById(6L)).thenReturn(slip);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/slips/6/image")
                        .header("Authorization", "Bearer token"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());

        org.mockito.Mockito.verify(slipService, org.mockito.Mockito.never()).getSlipFile(org.mockito.ArgumentMatchers.anyString());
    }
}
