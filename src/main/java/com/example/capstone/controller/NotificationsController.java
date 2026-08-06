package com.example.capstone.controller;

import com.example.capstone.dto.DeviceListDto;
import com.example.capstone.dto.UserDeviceRequest;
import com.example.capstone.dto.UserDeviceResponse;
import com.example.capstone.entity.NotificationLog;
import com.example.capstone.service.NotificationService;
import jakarta.validation.Valid;
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
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.getAllNotificationLogs(token, PageRequest.of(page, size, Sort.by("sentAt").descending())
        );
    }

    @GetMapping("/logs/filter-refType/{refType}")
    public Page<NotificationLog> getNotificationsByRefType(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("refType") String refType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.getNotificationLogs(
                token,
                refType,
                PageRequest.of(page, size, Sort.by("sentAt").descending())
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

    @GetMapping("/logs/{logId}/clicked")
    public void markAsClicked(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("logId") UUID logId
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        notificationsService.markNotificationAsClicked(token, logId);
    }


    @GetMapping("/devices/my-devices")
    public List<DeviceListDto> getMyDevices(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.getMyDevices(token);
    }

    @PostMapping("/devices/register")
    public UserDeviceResponse registerDevice(@RequestHeader("Authorization") String authorizationHeader,
                                             @Valid @RequestBody UserDeviceRequest userDeviceRequest) {
        String token = authorizationHeader.replace("Bearer ", "");
        return notificationsService.registerDevice(token, userDeviceRequest);
    }

    @DeleteMapping("/devices/{deviceId}")
    public void unregisterDevice(@RequestHeader("Authorization") String authorizationHeader,
                                 @PathVariable("deviceId") UUID deviceId) {
        String token = authorizationHeader.replace("Bearer ", "");
        notificationsService.unregisterDevice(token, deviceId);
    }
}
