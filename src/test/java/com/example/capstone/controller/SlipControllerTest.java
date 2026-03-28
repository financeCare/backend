package com.example.capstone.controller;

import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.service.SlipService;
import com.example.capstone.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SlipControllerTest {

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

    @Autowired
    private ObjectMapper objectMapper;

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
}
