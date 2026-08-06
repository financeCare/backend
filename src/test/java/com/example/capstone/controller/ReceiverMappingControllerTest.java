package com.example.capstone.controller;

import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.ReceiverMappingService;
import com.example.capstone.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiverMappingController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class ReceiverMappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiverMappingService receiverMappingService;
    
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetMyMappings_Success() throws Exception {
        ReceiverMapping mapping = ReceiverMapping.builder().id(1L).receiverName("Shop A").build();

        when(receiverMappingService.getMappingsByUser(anyString())).thenReturn(Arrays.asList(mapping));

        mockMvc.perform(get("/receiver-mappings")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].receiverName").value("Shop A"));
    }

    @Test
    void testSaveMapping_Success() throws Exception {
        ReceiverMappingController.MappingRequest request = new ReceiverMappingController.MappingRequest();
        request.setReceiverName("Shop A");
        request.setCategoryId(1);

        ReceiverMapping savedMapping = ReceiverMapping.builder()
                .id(1L)
                .receiverName("Shop A")
                .build();

        when(receiverMappingService.createOrUpdateMapping(anyString(), anyString(), anyInt(), any()))
                .thenReturn(savedMapping);

        mockMvc.perform(post("/receiver-mappings")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiverName").value("Shop A"));
    }

    @Test
    void testDeleteMapping_Success() throws Exception {
        doNothing().when(receiverMappingService).deleteMapping(anyLong(), anyString());

        mockMvc.perform(delete("/receiver-mappings/1")
                        .header("Authorization", "Bearer mock-token"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }
}
