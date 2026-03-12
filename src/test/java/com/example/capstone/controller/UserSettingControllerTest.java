package com.example.capstone.controller;

import com.example.capstone.dto.UserSettingDto;
import com.example.capstone.dto.UserSettingResponseDto;
import com.example.capstone.entity.UserSetting;
import com.example.capstone.security.JwtUtil;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserSettingController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class UserSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetUserSetting() throws Exception {
        UserSetting setting = new UserSetting();
        setting.setNotificationsEnabled(true);

        UserSettingResponseDto response = new UserSettingResponseDto();
        response.setUserSettingList(setting);

        when(userService.getUserSetting(anyString())).thenReturn(response);

        mockMvc.perform(get("/user-setting")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userSettingList.notificationsEnabled").value(true));
    }

    @Test
    void testSetUserUserSetting() throws Exception {
        UserSettingDto dto = new UserSettingDto();
        dto.setNotificationsEnabled(false);

        UserSetting setting = new UserSetting();
        setting.setNotificationsEnabled(false);

        when(userService.setUserSetting(anyString(), any(UserSettingDto.class))).thenReturn(setting);

        mockMvc.perform(put("/user-setting")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEnabled").value(false));
    }
}
