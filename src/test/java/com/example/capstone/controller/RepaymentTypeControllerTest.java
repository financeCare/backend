package com.example.capstone.controller;

import com.example.capstone.dto.RepaymentTypeRequest;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.RepaymentTypeService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepaymentTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RepaymentTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.example.capstone.security.AdminAccess adminAccess;

    @MockitoBean
    private RepaymentTypeService repaymentTypeService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetAllRepaymentTypes() throws Exception {
        RepaymentType type = new RepaymentType();
        type.setRepaymentTypeId(1);
        type.setRepaymentTypeName("EMI");
        List<RepaymentType> types = Arrays.asList(type);

        when(repaymentTypeService.getAllRepaymentTypes()).thenReturn(types);

        mockMvc.perform(get("/repayment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repaymentTypeName").value("EMI"));
    }

    @Test
    void testAddRepaymentType() throws Exception {
        RepaymentTypeRequest type = new RepaymentTypeRequest("BULLET", "Pay everything at maturity");

        when(repaymentTypeService.addRepaymentType(any(RepaymentTypeRequest.class))).thenReturn("Success");

        mockMvc.perform(post("/repayment-types").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(type)))
                .andExpect(status().isCreated())
                .andExpect(content().string("Success"));
    }

    @Test
    void testDeleteRepaymentType() throws Exception {
        when(repaymentTypeService.deleteRepaymentType(anyInt())).thenReturn("Deleted");

        mockMvc.perform(delete("/repayment-types/1").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted"));
    }

    @Test
    void sharedDataCanOnlyBeChangedByAnAdministrator() throws Exception {
        org.mockito.Mockito.doThrow(new com.example.capstone.exception.BusinessException("Forbidden", org.springframework.http.HttpStatus.FORBIDDEN))
                .when(adminAccess).requireAdmin(org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/repayment-types").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repaymentTypeName\":\"EMI\",\"repaymentTypeDescription\":\"Monthly\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/repayment-types/1").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(repaymentTypeService);
    }
}
