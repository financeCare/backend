package com.example.capstone.controller;

import com.example.capstone.dto.UserDeviceRequest;
import com.example.capstone.dto.UserDeviceResponse;
import com.example.capstone.entity.NotificationLog;
import com.example.capstone.enums.NotificationChannel;
import com.example.capstone.enums.NotificationStatus;
import com.example.capstone.enums.RefType;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.NotificationService;
import com.example.capstone.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationsController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class NotificationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Test
    void testGetAllNotifications() throws Exception {
        NotificationLog log = new NotificationLog();
        log.setTitle("Test Notification");
        log.setRefId(UUID.randomUUID().toString());
        log.setRefType(RefType.DEBT);
        log.setUserId(UUID.randomUUID());
        log.setBody("Test Body");
        log.setChannel(NotificationChannel.PUSH);
        log.setStatus(NotificationStatus.SENT);
        log.setRuleId(UUID.randomUUID());
        log.setScheduleKey("test");
        log.setErrorMessage("test");
        log.setSentAt(LocalDateTime.now());
        log.setLogId(UUID.randomUUID());
        Page<NotificationLog> page = new PageImpl<>(Arrays.asList(log));

        when(notificationsService.getAllNotificationLogs(anyString(), any())).thenReturn(page);

        MockHttpServletRequestBuilder requestBuilder = get("/notifications/logs")
                .header("Authorization", "Bearer token");

        ResultActions resultActions = mockMvc.perform(requestBuilder);

        resultActions.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Test Notification"));
    }

    @Test
    void testGetNotificationsByRefType() throws Exception {
        NotificationLog log = new NotificationLog();
        log.setRefType(RefType.DEBT);
        Page<NotificationLog> page = new PageImpl<>(Arrays.asList(log));

        when(notificationsService.getNotificationLogs(anyString(), anyString(), any())).thenReturn(page);

        MockHttpServletRequestBuilder requestBuilder = get("/notifications/logs/filter-refType/DEBT")
                .header("Authorization", "Bearer token");

        ResultActions resultActions = mockMvc.perform(requestBuilder);

        resultActions.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].refType").value("DEBT"));
    }

    @Test
    void testUnreadCount() throws Exception {
        when(notificationsService.countUnreadNotifications(anyString())).thenReturn(5);

        MockHttpServletRequestBuilder requestBuilder = get("/notifications/logs/unread-count")
                .header("Authorization", "Bearer token");

        ResultActions resultActions = mockMvc.perform(requestBuilder);

        resultActions.andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void testRegisterDevice() throws Exception {
        UserDeviceRequest request = new UserDeviceRequest();
        request.setFcmToken("fcm-token");
        request.setDeviceKey("device-key");
        request.setPlatform("android");
        request.setDeviceName("test-device");

        UserDeviceResponse response = new UserDeviceResponse();
        response.setDeviceId(UUID.randomUUID());

        when(notificationsService.registerDevice(anyString(), any(UserDeviceRequest.class))).thenReturn(response);

        MockHttpServletRequestBuilder requestBuilder = post("/notifications/devices/register")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));

        ResultActions resultActions = mockMvc.perform(requestBuilder);

        resultActions.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").exists());
    }

    @Test
    void testUnregisterDevice() throws Exception {
        UUID deviceId = UUID.randomUUID();
        doNothing().when(notificationsService).unregisterDevice(anyString(), any(UUID.class));

        MockHttpServletRequestBuilder requestBuilder = delete("/notifications/devices/" + deviceId)
                .header("Authorization", "Bearer token");

        ResultActions resultActions = mockMvc.perform(requestBuilder);

        resultActions.andDo(print())
                .andExpect(status().isOk());

        verify(notificationsService, times(1)).unregisterDevice(anyString(), eq(deviceId));
    }
}
