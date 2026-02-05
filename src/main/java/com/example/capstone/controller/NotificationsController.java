package com.example.capstone.controller;

import com.example.capstone.dto.DeviceListDto;
import com.example.capstone.dto.NotificationCreateDTO;
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
        return notificationsService.registerDevice(token, userDeviceRequest);
    }

    @DeleteMapping("/devices/{deviceId}")
    public void unregisterDevice(@RequestHeader("Authorization") String authorizationHeader,
                                 @PathVariable UUID deviceId) {
        String token = authorizationHeader.replace("Bearer ", "");
        notificationsService.unregisterDevice(token, deviceId);
    }

//    @GetMapping
//    public Page<Notifications> getMyNotifications(
//            @RequestHeader("Authorization") String authorizationHeader,
//            @RequestParam(required = false) Boolean isRead,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        String token = authorizationHeader.replace("Bearer ", "");
//        return notificationsService.getMyNotifications(
//                token,
//                isRead,
//                PageRequest.of(page, size, Sort.by("createdAt").descending())
//        );
//    }
//
//    @GetMapping("/unread-count")
//    public long unreadCount(@RequestHeader("Authorization") String authorizationHeader) {
//        String token = authorizationHeader.replace("Bearer ", "");
//        return notificationsService.countUnread(token);
//    }
//
//    @PostMapping
//    public Notifications create(@RequestHeader("Authorization") String authorizationHeader,
//                                @RequestBody NotificationCreateDTO dto) {
//        String token = authorizationHeader.replace("Bearer ", "");
//        return notificationsService.createForMe(token, dto);
//    }
//
//    @PatchMapping("/{id}/read")
//    public Notifications markAsRead(@RequestHeader("Authorization") String authorizationHeader,
//                                    @PathVariable UUID id) {
//        String token = authorizationHeader.replace("Bearer ", "");
//        return notificationsService.markAsRead(token, id);
//    }
//
//    @PatchMapping("/read-all")
//    public void markAllAsRead(@RequestHeader("Authorization") String authorizationHeader) {
//        String token = authorizationHeader.replace("Bearer ", "");
//        notificationsService.markAllAsRead(token);
//    }
//
//    @DeleteMapping("/{id}")
//    public void delete(@RequestHeader("Authorization") String authorizationHeader,
//                       @PathVariable UUID id) {
//        String token = authorizationHeader.replace("Bearer ", "");
//        notificationsService.deleteMyNotification(token, id);
//    }
}
