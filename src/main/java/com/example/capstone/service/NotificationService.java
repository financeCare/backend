package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.entity.*;
import com.example.capstone.enums.NotificationChannel;
import com.example.capstone.enums.NotificationStatus;
import com.example.capstone.enums.RefType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.FirebaseMessaging;


@Service
@RequiredArgsConstructor
public class NotificationService {
    private final UserService userService;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSettingRepository userSettingRepository;
    private final NotificationRuleRepository notificationRuleRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final DebtRepository debtRepository;

    public static LocalDate dueDateOfThisMonth(int dueDay, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate first = today.withDayOfMonth(1);
        int lastDay = first.lengthOfMonth();
        int safeDay = Math.min(Math.max(dueDay, 1), lastDay);
        return first.withDayOfMonth(safeDay);
    }

    // ถ้าอยากให้ “ถ้าวันนี้เลย dueDate แล้ว” ให้ไปรอบเดือนหน้า (optional)
    public static LocalDate nextDueDate(int dueDay, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate dueThisMonth = dueDateOfThisMonth(dueDay, zoneId);
        if (today.isAfter(dueThisMonth)) {
            LocalDate firstNextMonth = today.plusMonths(1).withDayOfMonth(1);
            int lastDay = firstNextMonth.lengthOfMonth();
            int safeDay = Math.min(Math.max(dueDay, 1), lastDay);
            return firstNextMonth.withDayOfMonth(safeDay);
        }
        return dueThisMonth;
    }

    public static LocalDate notifyDate(LocalDate dueDate, int remindDaysBefore) {
        return dueDate.minusDays(remindDaysBefore);
    }

    public UserDeviceResponse registerDevice  (String token,UserDeviceRequest userDeviceRequest) {
        UUID userId = userService.extractUserIdFromToken(token);
        LocalDateTime currentTime = LocalDateTime.now();
        UserDevice existingDevice = userDeviceRepository.findByDeviceKeyAndUserId(userDeviceRequest.getDeviceKey(), userId);
        if (existingDevice != null) {
            existingDevice.setLastSeen(currentTime);
            existingDevice.setFcmToken(userDeviceRequest.getFcmToken());
            userDeviceRepository.save(existingDevice);
            return new UserDeviceResponse(existingDevice.getDeviceId(),existingDevice.isActive(),currentTime);
        }
        UserDevice userDevice = new UserDevice();
        userDevice.setUserId(userId);
        userDevice.setFcmToken(userDeviceRequest.getFcmToken());
        userDevice.setPlatform(userDeviceRequest.getPlatform());
        userDevice.setDeviceName(userDevice.getDeviceName());
        userDevice.setLastSeen(currentTime);
        userDevice.setDeviceKey(userDeviceRequest.getDeviceKey());
        userDevice.setActive(true);
        userDeviceRepository.save(userDevice);
        return new UserDeviceResponse(userDevice.getDeviceId(),userDevice.isActive(),currentTime);
    }

    public List<DeviceListDto> getMyDevices(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<UserDevice> userDevices = userDeviceRepository.findAllByUserIdAndIsActiveTrue(userId);
        return userDevices.stream()
                .map(ud -> new DeviceListDto(
                        ud.getDeviceId(),
                        ud.getDeviceName(),
                        ud.getPlatform(),
                        ud.getLastSeen()
                ))
                .toList();
    }

    public void unregisterDevice(String token, UUID deviceId) {
        UUID userId = userService.extractUserIdFromToken(token);
        UserDevice userDevice = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException("Device not found", HttpStatus.NOT_FOUND));
        if (!userDevice.getUserId().equals(userId)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        userDevice.setActive(false);
        userDeviceRepository.save(userDevice);
    }

    //TODO: create notification rule for debt implementation with de
    public void createNotificationRuleForDebt(UUID userId,String debtName,Double minPayment,UUID debtId) {
        UserSetting userSetting = userSettingRepository.findById(userId).orElseThrow(() -> new BusinessException("User id is not found", HttpStatus.NOT_FOUND));
        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setUserId(userId);
        notificationRule.setRefId(debtId.toString());
        notificationRule.setRefType(RefType.DEBT);
        notificationRule.setTitle("เตือนหนี้ครบกำหนด");
        notificationRule.setBodyTemplate("หนี้ "+ debtName + " จำนวน " + minPayment+  " บาทครบกำหนดวันนี้");
        notificationRule.setRemindDaysBefore(userSetting.getDefaultRemindDaysBefore());
        notificationRule.setTimeOfDay(userSetting.getDefaultNotifyTime());
        notificationRule.setTimezone(userSetting.getTimezone());
        notificationRuleRepository.save(notificationRule);
    }

    public void createNotificationRuleForBudget(UUID userId,String categoryName,Double limitAmount,Double amount) {
        double usedPercentage = (amount / limitAmount) * 100;
        if (usedPercentage < 90) {
        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setUserId(userId);
        notificationRule.setRefType(RefType.BUDGET);
        notificationRule.setTitle("เตือนงบประมาณใกล้ครบกำหนด");
        notificationRule.setBodyTemplate("งบประมาณของ " + categoryName+" คงเหลือ " + (limitAmount - amount) + " บาท ("+String.format("%.2f",usedPercentage)+"% ของงบประมาณทั้งหมด)");
        notificationRule.setRemindDaysBefore(0);
        notificationRule.setTimeOfDay(null);
        notificationRule.setTimezone("Asia/Bangkok");
        notificationRuleRepository.save(notificationRule);
        }
    }

    public NotificationRule getNotificationRuleById(String token ,UUID ruleId) {
        UUID userId = userService.extractUserIdFromToken(token);
        return notificationRuleRepository.findByRuleIdAndUserId(ruleId,userId)
                .orElseThrow(() -> new BusinessException("Notification rule not found", HttpStatus.NOT_FOUND));
    }

    public void deleteNotificationRule(String token,UUID ruleId) {
        UUID userId = userService.extractUserIdFromToken(token);
        NotificationRule notificationRule = notificationRuleRepository.findByRuleIdAndUserId(ruleId,userId)
                .orElseThrow(() -> new BusinessException("Notification rule not found", HttpStatus.NOT_FOUND));
        notificationRuleRepository.delete(notificationRule);
    }

    public void deleteNotificationRulesByRefIdAndRefType(String token,UUID refId) {
        UUID userId = userService.extractUserIdFromToken(token);
        NotificationRule notificationRules = notificationRuleRepository.findByUserIdAndRefTypeAndRefId(userId,RefType.DEBT,refId.toString()).orElseThrow(()-> new BusinessException("Notification rule not found", HttpStatus.NOT_FOUND));
        notificationRuleRepository.delete(notificationRules);
    }

    public Page<NotificationLog> getNotificationLogs(String token, String refType ,Pageable pageable) {
        UUID userId = userService.extractUserIdFromToken(token);
        return notificationLogRepository.findAllByUserIdAndRefType(userId, RefType.valueOf(refType), pageable);
    }

    public Page<NotificationLog> getAllNotificationLogs(String token, Pageable pageable) {
        UUID userId = userService.extractUserIdFromToken(token);
        System.out.println(userId);
        return notificationLogRepository.findAllByUserId(userId, pageable);
    }

    public int countUnreadNotifications(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        return (int) notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())
                .stream()
                .filter(log -> log.getStatus() == NotificationStatus.SENT)
                .count();
    }

    public void markAllNotificationsAsRead(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<NotificationLog> logs = notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())
                .stream()
                .filter(log -> log.getStatus() == NotificationStatus.SENT)
                .toList();
        for (NotificationLog log : logs) {
            log.setStatus(NotificationStatus.CLICKED);
        }
        notificationLogRepository.saveAll(logs);
    }
    @Scheduled(cron = "0 * * * * *") // ทุก 1 นาที
    public void runNotificationScheduler() {
        List<NotificationRule> rules =
                notificationRuleRepository.findAllByIsActive(true);
        for (NotificationRule rule : rules) {
            processRule(rule);
        }
    }

    private void processRule(NotificationRule rule) {
        UUID userId = rule.getUserId();

        UserSetting setting = userSettingRepository
                .findById(userId)
                .orElse(null);
        if (setting == null) return;

        // master switch
        if (!setting.isNotificationsEnabled()) return;

        // example: DEBT
        if (rule.getRefType() == RefType.DEBT) {
            Debt debt = debtRepository
                    .findById(UUID.fromString(rule.getRefId()))
                    .orElse(null);
            if (debt == null || !debt.isActive()) return;

            boolean shouldSend = shouldSendToday(
                    debt.getDueDay(),                      // int day of month
                    rule.getRemindDaysBefore(),
                    rule.getTimeOfDay(),                  // LocalTime
                    setting.getTimezone()                 // String
            );

            if (shouldSend) {
                sendDebtNotification(userId, rule.getRuleId(), debt,
                        rule.getRemindDaysBefore(),
                        setting.getTimezone()
                );
            }
        }
    }

    private boolean shouldSendToday(
            int dueDay,
            int remindDaysBefore,
            LocalTime timeOfDay,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of((timezone == null || timezone.isBlank()) ? "Asia/Bangkok" : timezone);

        LocalDate today = LocalDate.now(zoneId);
        LocalTime nowTime = LocalTime.now(zoneId);

        // ใช้รอบ "เดือนนี้" (หรือเปลี่ยนเป็น nextDueDate(...) ได้)
        LocalDate dueDate = dueDateOfThisMonth(dueDay, zoneId);
        LocalDate notifyDate = notifyDate(dueDate, remindDaysBefore);

        // วันนี้ใช่วันแจ้งเตือนหรือไม่
        if (!today.equals(notifyDate)) return false;

        // เวลาถึงเวลาแจ้งหรือยัง
        LocalTime notifyTime = (timeOfDay != null) ? timeOfDay : LocalTime.of(9, 0);
        return !nowTime.isBefore(notifyTime); // now >= notifyTime
    }

    @Transactional
    public void sendDebtNotification(UUID userId, UUID ruleId, Debt debt, int remindDaysBefore, String timezone) {

        // 1) กันส่งซ้ำในวันเดียวกัน (SENT เท่านั้น)
        ZoneId zoneId = ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Bangkok" : timezone);
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);

        boolean alreadySentToday = notificationLogRepository
                .existsByRuleIdAndStatusAndSentAtBetween(ruleId, NotificationStatus.SENT, start, end);

        if (alreadySentToday) {
            return;
        }

        // 2) คำนวณ dueDate ของรอบนี้ (ตัวอย่าง: ใช้ dueDay เป็นหลัก)
        LocalDate dueDate = getDueDateForThisMonth(debt.getDueDay(), zoneId);

        // 3) เช็คว่าควรส่งวันนี้ไหม (วันนี้ = dueDate - remindDaysBefore)
        LocalDate notifyDate = dueDate.minusDays(remindDaysBefore);
        if (!today.equals(notifyDate)) {
            return;
        }

        // 4) สร้างข้อความ
        String title = "เตือนหนี้ครบกำหนด";
        String body = buildDebtBody(debt, dueDate, remindDaysBefore);

        // 5) ดึง device tokens ที่ active
        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndIsActiveTrue(userId);

        if (devices.isEmpty()) {
            // ไม่มีอุปกรณ์ -> log failed (เพื่อ debug)
            saveLog(userId, ruleId, RefType.DEBT, String.valueOf(debt.getDebtId()),
                    NotificationChannel.PUSH, NotificationStatus.FAILED,
                    title, body, "No active device tokens");
            return;
        }

        // 6) ส่งให้ทุก device
        for (UserDevice d : devices) {
            try {
                Message message = Message.builder()
                        .setToken(d.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        // ✅ ส่ง data เพื่อให้ Flutter เปิดหน้าที่เกี่ยวข้องได้
                        .putData("refType", "DEBT")
                        .putData("refId", String.valueOf(debt.getDebtId()))
                        .build();

                FirebaseMessaging.getInstance().send(message);

                saveLog(userId, ruleId, RefType.DEBT, String.valueOf(debt.getDebtId()),
                        NotificationChannel.PUSH, NotificationStatus.SENT,
                        title, body, null);

            } catch (Exception ex) {
                // token อาจตาย/invalid -> คุณอาจเลือกปิด device นี้ด้วยก็ได้
                // d.setActive(false); userDeviceRepository.save(d);

                saveLog(userId, ruleId, RefType.DEBT, String.valueOf(debt.getDebtId()),
                        NotificationChannel.PUSH, NotificationStatus.FAILED,
                        title, body, ex.getMessage());
            }
        }
    }

    private void saveLog(
            UUID userId,
            UUID ruleId,
            RefType refType,
            String refId,
            NotificationChannel channel,
            NotificationStatus status,
            String title,
            String body,
            String error
    ) {
        NotificationLog log = NotificationLog.builder()
                .userId(userId)
                .ruleId(ruleId)
                .refType(refType)
                .refId(refId)
                .channel(channel)
                .status(status)
                .title(title)
                .body(body)
                .errorMessage(error)
                .build();

        notificationLogRepository.save(log);
    }

    private String buildDebtBody(Debt debt, LocalDate dueDate, int remindDaysBefore) {
        if (remindDaysBefore == 0) {
            return "หนี้ " + debt.getDebtName() + " จำนวน " + debt.getMinPayment() + " บาทครบกำหนดวันนี้";
        }
        return "อีก " + remindDaysBefore + " วัน จะถึงกำหนดชำระหนี้ " + debt.getDebtName()
                + " (กำหนด " + dueDate + ") ขั้นต่ำ " + debt.getMinPayment() + " บาท";
    }

    private LocalDate getDueDateForThisMonth(int dueDay, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        int lastDay = today.lengthOfMonth();
        int day = Math.min(dueDay, lastDay);
        return today.withDayOfMonth(day);
    }

}

