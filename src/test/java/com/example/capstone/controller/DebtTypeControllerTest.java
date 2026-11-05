package com.example.capstone.controller;

import com.example.capstone.dto.DebtTypeRequest;
import com.example.capstone.entity.DebtType;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.DebtTypeService;
import com.example.capstone.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(DebtTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DebtTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.example.capstone.security.AdminAccess adminAccess;

    @MockitoBean
    private DebtTypeService debtTypeService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetAllDebtTypes() throws Exception {
        DebtType debtType = new DebtType();
        debtType.setDebtTypeId(1);
        debtType.setDebtTypeName("Personal Loan");
        List<DebtType> debtTypes = Arrays.asList(debtType);

        when(debtTypeService.getAllDebtTypes()).thenReturn(debtTypes);

        mockMvc.perform(get("/debt-types"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].debtTypeName").value("Personal Loan"));

    }

    @Test
    void testAddDebtType() throws Exception {
        DebtTypeRequest debtType = new DebtTypeRequest("Credit Card", "Credit Card Description");

        when(debtTypeService.addDebtType(any(DebtTypeRequest.class))).thenReturn("Success");

        mockMvc.perform(post("/debt-types").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debtType)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().string("Success"));

    }

    @Test
    void testDeleteDebtType() throws Exception {
        when(debtTypeService.deleteDebtType(anyInt())).thenReturn("Deleted");

        mockMvc.perform(delete("/debt-types/1").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted"));
    }

    @Test
    void sharedDataCanOnlyBeChangedByAnAdministrator() throws Exception {
        org.mockito.Mockito.doThrow(new com.example.capstone.exception.BusinessException("Forbidden", org.springframework.http.HttpStatus.FORBIDDEN))
                .when(adminAccess).requireAdmin(org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/debt-types").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"debtTypeName\":\"Card\",\"debtTypeDescription\":\"Credit card\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/debt-types/1").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(debtTypeService);
    }
}
