package com.example.capstone.controller;

import com.example.capstone.dto.DeviceListDto;
//import com.example.capstone.entity.Notifications;
import com.example.capstone.dto.UserDeviceRequest;
import com.example.capstone.dto.UserDeviceResponse;
import com.example.capstone.entity.NotificationLog;
import com.example.capstone.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationService notificationsService;

    @GetMapping("/logs")
    public Page<NotificationLog> getAllNotifications(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.getAllNotificationLogs(token, PageRequest.of(page, size, Sort.by("sentAt").descending())
        );
    }

    @GetMapping("/logs/filter-refType/{refType}")
    public Page<NotificationLog> getNotificationsByRefType(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String refType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.getNotificationLogs(
                token,
                refType,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }

    @GetMapping("/logs/unread-count")
    public long unreadCount(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.countUnreadNotifications(token);
    }

    @GetMapping("/logs/read-all")
    public void maskAsReadAll(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        notificationsService.markAllNotificationsAsRead(token);
    }

    @GetMapping("/devices/my-devices")
    public List<DeviceListDto> getMyDevices(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.getMyDevices(token);
    }

    @PostMapping("/devices/register")
    public UserDeviceResponse registerDevice(@RequestHeader("Authorization") String authorizationHeader,
                                             @RequestBody UserDeviceRequest userDeviceRequest) {
        String token = authorizationHeader.replace("Bearer ", "");
        System.out.println("register devices");
        return notificationsService.registerDevice(token, userDeviceRequest);
    }

    @DeleteMapping("/devices/{deviceId}")
    public void unregisterDevice(@RequestHeader("Authorization") String authorizationHeader,
                                 @PathVariable UUID deviceId) {
        String token = authorizationHeader.replace("Bearer ", "");
        notificationsService.unregisterDevice(token, deviceId);
    }
}
