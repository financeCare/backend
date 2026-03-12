package com.example.capstone.controller;

import com.example.capstone.dto.DebtDTO;
import com.example.capstone.dto.DebtPaymentRequestDTO;
import com.example.capstone.entity.Debt;
import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.DebtService;
import com.example.capstone.service.RepaymentService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(DebtController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class DebtControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DebtService debtService;

    @MockitoBean
    private RepaymentService repaymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetOwnDebt() throws Exception {
        Debt debt = new Debt();
        debt.setDebtName("Home Loan");
        List<Debt> debts = Arrays.asList(debt);

        when(debtService.getOwnDebt(anyString())).thenReturn(debts);

        mockMvc.perform(get("/debts")
                        .header("Authorization", "Bearer token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].debtName").value("Home Loan"));

    }

    @Test
    void testGetDebtDetail() throws Exception {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setDebtId(debtId);
        debt.setDebtName("Home Loan");

        when(debtService.getDebtDetail(anyString(), any(UUID.class))).thenReturn(debt);

        mockMvc.perform(get("/debts/" + debtId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtName").value("Home Loan"));
    }

    @Test
    void testCreateDebt() throws Exception {
        DebtDTO dto = new DebtDTO();
        dto.setDebtName("Car Loan");
        dto.setPrincipalAmount(new java.math.BigDecimal(200000));
        dto.setInterestRate(new java.math.BigDecimal(5));
        dto.setRepaymentTypeId(1);
        dto.setStartDate(new java.util.Date());
        dto.setDebtTypeId(1);
        dto.setMinPayment(new java.math.BigDecimal(5000));
        dto.setDueDay(1);
        dto.setInterestCalculationType(InterestCalculationType.THIRTY_360);

        Debt debt = new Debt();
        debt.setDebtName("Car Loan");

        when(debtService.addDebt(anyString(), any(DebtDTO.class))).thenReturn(debt);

        mockMvc.perform(post("/debts")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debtName").value("Car Loan"));

    }

    @Test
    void testUpdateDebt() throws Exception {
        UUID debtId = UUID.randomUUID();
        DebtDTO dto = new DebtDTO();
        dto.setDebtName("Updated Loan");
        dto.setPrincipalAmount(new java.math.BigDecimal(200000));
        dto.setInterestRate(new java.math.BigDecimal(5));
        dto.setRepaymentTypeId(1);
        dto.setStartDate(new java.util.Date());
        dto.setDebtTypeId(1);
        dto.setMinPayment(new java.math.BigDecimal(5000));
        dto.setDueDay(1);
        dto.setInterestCalculationType(InterestCalculationType.THIRTY_360);

        Debt debt = new Debt();
        debt.setDebtId(debtId);
        debt.setDebtName("Updated Loan");

        when(debtService.updateDebt(anyString(), any(UUID.class), any(DebtDTO.class))).thenReturn(debt);

        mockMvc.perform(put("/debts/" + debtId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtName").value("Updated Loan"));

    }

    @Test
    void testDeleteDebt() throws Exception {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setDebtId(debtId);

        when(debtService.deleteDebt(anyString(), any(UUID.class))).thenReturn(debt);

        mockMvc.perform(delete("/debts/" + debtId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtId").value(debtId.toString()));
    }

    @Test
    void testPayDebt() throws Exception {
        DebtPaymentRequestDTO dto = new DebtPaymentRequestDTO();
        dto.setDebtId(UUID.randomUUID());
        dto.setPaymentAmount(new java.math.BigDecimal(5000));
        dto.setPaymentDate(java.time.LocalDate.now());

        doNothing().when(repaymentService).payDebt(anyString(), any(DebtPaymentRequestDTO.class));

        mockMvc.perform(post("/debts/pays")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(repaymentService, times(1)).payDebt(anyString(), any(DebtPaymentRequestDTO.class));
    }
}
