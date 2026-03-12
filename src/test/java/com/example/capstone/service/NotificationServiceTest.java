package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.entity.*;
import com.example.capstone.enums.NotificationStatus;
import com.example.capstone.enums.RefType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private UserSettingRepository userSettingRepository;
    @Mock
    private NotificationRuleRepository notificationRuleRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private DebtRepository debtRepository;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock-token";
    }

    @Test
    void testRegisterDevice_NewDevice() {
        UserDeviceRequest req = new UserDeviceRequest();
        req.setFcmToken("fcm-token");
        req.setDeviceKey("device-key");
        req.setPlatform("android");
        req.setDeviceName("Samsung S21");

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(userDeviceRepository.findByFcmToken(req.getFcmToken())).thenReturn(Optional.empty());
        when(userDeviceRepository.findByDeviceKeyAndUserId(req.getDeviceKey(), userId)).thenReturn(Optional.empty());

        UserDeviceResponse result = notificationService.registerDevice(token, req);

        assertNotNull(result);
        verify(userDeviceRepository).save(any(UserDevice.class));
    }

    @Test
    void testGetMyDevices() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        UserDevice device = new UserDevice();
        device.setDeviceId(UUID.randomUUID());
        device.setDeviceName("My Device");
        when(userDeviceRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(device));

        List<DeviceListDto> result = notificationService.getMyDevices(token);

        assertFalse(result.isEmpty());
        assertEquals("My Device", result.get(0).getDeviceName());
    }

    @Test
    void testUnregisterDevice_Success() {
        UUID deviceId = UUID.randomUUID();
        UserDevice device = new UserDevice();
        device.setDeviceId(deviceId);
        device.setUserId(userId);
        device.setActive(true);

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(userDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        notificationService.unregisterDevice(token, deviceId);

        assertFalse(device.getActive());
        verify(userDeviceRepository).save(device);
    }

    @Test
    void testUnregisterDevice_Forbidden() {
        UUID deviceId = UUID.randomUUID();
        UserDevice device = new UserDevice();
        device.setDeviceId(deviceId);
        device.setUserId(UUID.randomUUID()); // Different user

        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(userDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            notificationService.unregisterDevice(token, deviceId);
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void testGetNotificationLogs() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        Page<NotificationLog> page = new PageImpl<>(List.of(new NotificationLog()));
        when(notificationLogRepository.findAllByUserIdAndRefTypeAndStatusIsNot(eq(userId), eq(RefType.BUDGET), eq(NotificationStatus.FAILED), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationLog> result = notificationService.getNotificationLogs(token, "BUDGET", Pageable.unpaged());

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testMarkAllNotificationsAsRead() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        NotificationLog log = new NotificationLog();
        log.setStatus(NotificationStatus.SENT);
        when(notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(log)));

        notificationService.markAllNotificationsAsRead(token);

        assertEquals(NotificationStatus.CLICKED, log.getStatus());
        verify(notificationLogRepository).saveAll(any());
    }
}
