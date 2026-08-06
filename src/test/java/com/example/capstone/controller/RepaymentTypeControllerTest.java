package com.example.capstone.controller;

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
public class RepaymentTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
        RepaymentType type = new RepaymentType();
        type.setRepaymentTypeName("BULLET");

        when(repaymentTypeService.addRepaymentType(any(RepaymentType.class))).thenReturn("Success");

        mockMvc.perform(post("/repayment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(type)))
                .andExpect(status().isCreated())
                .andExpect(content().string("Success"));
    }

    @Test
    void testDeleteRepaymentType() throws Exception {
        when(repaymentTypeService.deleteRepaymentType(anyInt())).thenReturn("Deleted");

        mockMvc.perform(delete("/repayment-types/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted"));
    }
}
