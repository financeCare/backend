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

    @Transactional
    public UserDeviceResponse registerDevice(String token, UserDeviceRequest req) {
        UUID userId = userService.extractUserIdFromToken(token);
        LocalDateTime now = LocalDateTime.now();

        // 1) กันชนด้วย unique key (fcm_token)
        if (req.getFcmToken() != null && !req.getFcmToken().isBlank()) {
            Optional<UserDevice> byTokenOpt = userDeviceRepository.findByFcmToken(req.getFcmToken());
            if (byTokenOpt.isPresent()) {
                UserDevice d = byTokenOpt.get();

                // update ให้เป็นข้อมูลล่าสุด (อาจเป็นคนละ user/deviceKey ก็ได้)
                d.setUserId(userId);
                d.setDeviceKey(req.getDeviceKey());
                d.setPlatform(req.getPlatform());
                d.setDeviceName(req.getDeviceName());
                d.setLastSeen(now);
                d.setActive(true);

                userDeviceRepository.save(d);
                return new UserDeviceResponse(d.getDeviceId(), d.isActive(), now);
            }
        }

        // 2) ถ้า token ยังไม่เคยมี → หา device เดิมด้วย deviceKey+userId
        Optional<UserDevice> byKeyUserOpt =
                userDeviceRepository.findByDeviceKeyAndUserId(req.getDeviceKey(), userId);

        if (byKeyUserOpt.isPresent()) {
            UserDevice d = byKeyUserOpt.get();
            d.setFcmToken(req.getFcmToken()); // token ใหม่ (ถ้ามี)
            d.setPlatform(req.getPlatform());
            d.setDeviceName(req.getDeviceName());
            d.setLastSeen(now);
            d.setActive(true);

            userDeviceRepository.save(d);
            return new UserDeviceResponse(d.getDeviceId(), d.isActive(), now);
        }

        // 3) ไม่เจอทั้งคู่ → create ใหม่
        UserDevice d = new UserDevice();
        d.setUserId(userId);
        d.setFcmToken(req.getFcmToken());
        d.setPlatform(req.getPlatform());
        d.setDeviceName(req.getDeviceName()); // ✅ แก้บั๊ก
        d.setLastSeen(now);
        d.setDeviceKey(req.getDeviceKey());
        d.setActive(true);

        userDeviceRepository.save(d);
        return new UserDeviceResponse(d.getDeviceId(), d.isActive(), now);
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
        System.out.println("debtId in create notifications function " + debtId);
        System.out.println("min payment in create notifications function " + minPayment);
        UserSetting userSetting = userSettingRepository.findById(userId).orElseThrow(() -> new BusinessException("User id is not found", HttpStatus.NOT_FOUND));
        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setUserId(userId);
        notificationRule.setRefId(debtId.toString());
        notificationRule.setRefType(RefType.DEBT);
        notificationRule.setTitle("เตือนหนี้ครบกำหนด");
        notificationRule.setBodyTemplate("หนี้ "+ debtName + " จำนวนขั้นต่ำ " + minPayment+  " บาทครบกำหนดวันนี้");
        notificationRule.setRemindDaysBefore(userSetting.getDefaultRemindDaysBefore());
        notificationRule.setTimeOfDay(userSetting.getDefaultNotifyTime());
        notificationRule.setTimezone(userSetting.getTimezone());
        notificationRuleRepository.save(notificationRule);
        System.out.println("create notification rule for debt called");
    }

    public void createNotificationRuleForBudget(UUID userId,String categoryName,Double limitAmount,Double amount,UUID budgetId) {
        double usedPercentage = (amount / limitAmount) * 100;
        if (usedPercentage > 90) {
        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setUserId(userId);
        notificationRule.setRefType(RefType.BUDGET);
        notificationRule.setTitle("เตือนงบประมาณใกล้ครบกำหนด");
        notificationRule.setBodyTemplate("งบประมาณของ " + categoryName+" คงเหลือ " + (limitAmount - amount) + " บาท ("+String.format("%.2f",usedPercentage)+"% ของงบประมาณทั้งหมด)");
        notificationRule.setRemindDaysBefore(0);
        notificationRule.setTimeOfDay(null);
        notificationRule.setTimezone("Asia/Bangkok");
        notificationRuleRepository.save(notificationRule);
        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndIsActiveTrue(userId);

            if (devices.isEmpty()) {
                saveLog(
                        userId, notificationRule.getRuleId(), null,
                        RefType.BUDGET, budgetId.toString(),
                        NotificationChannel.PUSH, NotificationStatus.FAILED,
                        notificationRule.getTitle(), notificationRule.getBodyTemplate(), "this user don't have any device"
                );
                return;
            }

            for (UserDevice d : devices) {
                try {
                    Message message = Message.builder()
                            .setToken(d.getFcmToken())
                            .setNotification(Notification.builder()
                                    .setTitle(notificationRule.getTitle())
                                    .setBody(notificationRule.getBodyTemplate())
                                    .build())
                            .putData("refType", "DEBT")
                            .putData("refId", budgetId.toString())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                } catch (Exception ex) {
                    saveLog(
                            userId, notificationRule.getRuleId(), null,
                            RefType.BUDGET, budgetId.toString(),
                            NotificationChannel.PUSH, NotificationStatus.FAILED,
                            notificationRule.getTitle(), notificationRule.getBodyTemplate(), ex.getMessage()
                    );
                }
            }

            saveLog(
                userId, notificationRule.getRuleId(), null,
                RefType.BUDGET, budgetId.toString(),
                NotificationChannel.PUSH, NotificationStatus.SENT,
                notificationRule.getTitle(), notificationRule.getBodyTemplate(), null
        );
        }
    }

    public Page<NotificationLog> getNotificationLogs(String token, String refType ,Pageable pageable) {
        UUID userId = userService.extractUserIdFromToken(token);
        return notificationLogRepository.findAllByUserIdAndRefTypeAndStatusIsNot(userId, RefType.valueOf(refType),NotificationStatus.FAILED, pageable);
    }

    public Page<NotificationLog> getAllNotificationLogs(String token, Pageable pageable) {
        UUID userId = userService.extractUserIdFromToken(token);
        System.out.println(userId);
        return notificationLogRepository.findAllByUserIdAndStatusIsNot(userId,NotificationStatus.FAILED, pageable);
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
        System.out.println("⏰ Scheduler running at " + LocalDateTime.now());
        List<NotificationRule> rules =
                notificationRuleRepository.findAllByIsActive(true);
        for (NotificationRule rule : rules) {
            processRule(rule);
        }
    }

    private void processRule(NotificationRule rule) {
        UUID userId = rule.getUserId();

        UserSetting setting = userSettingRepository.findById(userId).orElse(null);
        if (setting == null) return;

        // master switch
        if (!setting.isNotificationsEnabled()) return;
        if (rule.getRefType() == RefType.DEBT) {
            Debt debt = debtRepository
                    .findById(UUID.fromString(rule.getRefId()))
                    .orElse(null);
            if (debt == null || !debt.isActive()){
                return;
            }
            String timezone = setting.getTimezone();
            LocalTime timeOfDay = setting.getDefaultNotifyTime();
            int remindDaysBefore = setting.getDefaultRemindDaysBefore(); // เช่น 3
            int[] daysList = new int[]{remindDaysBefore, 0};

            for (int daysBefore : daysList) {
                boolean sendNow = shouldSendNow(debt.getDueDay(), daysBefore, timeOfDay, timezone);
                if (sendNow) {
                    sendDebtNotification(userId, rule.getRuleId(), debt, daysBefore, timezone, timeOfDay);
                }
            }

        }
    }

    private boolean shouldSendNow(
            int dueDay,
            int remindDaysBefore,
            LocalTime timeOfDay,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of((timezone == null || timezone.isBlank()) ? "Asia/Bangkok" : timezone);

        LocalDate today = LocalDate.now(zoneId);
        LocalTime nowTime = LocalTime.now(zoneId);

        LocalDate dueDate = getDueDateForThisMonth(dueDay, zoneId);
        LocalDate notifyDate = dueDate.minusDays(remindDaysBefore);

        if (!today.equals(notifyDate)) {
            return false;
        }

        LocalTime notifyTime = (timeOfDay != null) ? timeOfDay : LocalTime.of(9, 0);

        // ส่งภายในหน้าต่าง 1 นาที เพื่อกัน scheduler วิ่งซ้ำ
        return !nowTime.isBefore(notifyTime) && nowTime.isBefore(notifyTime.plusMinutes(1));
    }

    @Transactional
    public void sendDebtNotification(
            UUID userId,
            UUID ruleId,
            Debt debt,
            int remindDaysBefore,
            String timezone,
            LocalTime timeOfDay
    ) {
        ZoneId zoneId = ZoneId.of((timezone == null || timezone.isBlank()) ? "Asia/Bangkok" : timezone);

        LocalDate today = LocalDate.now(zoneId);
        LocalTime nowTime = LocalTime.now(zoneId);
        LocalTime notifyTime = (timeOfDay != null) ? timeOfDay : LocalTime.of(9, 0);

        // ✅ เช็คเวลาอีกชั้น เผื่อถูกเรียกจากที่อื่น
        if (nowTime.isBefore(notifyTime) || !nowTime.isBefore(notifyTime.plusMinutes(1))) {
            return;
        }

        LocalDate dueDate = getDueDateForThisMonth(debt.getDueDay(), zoneId);
        LocalDate notifyDate = dueDate.minusDays(remindDaysBefore);

        if (!today.equals(notifyDate)) return;

        // ✅ กันส่งซ้ำแบบ “ต่อวัน + ต่อครั้ง (daysBefore)”
        // ทำ key ให้ต่างกันระหว่าง "ล่วงหน้า" กับ "วันครบกำหนด"
        String scheduleKey = buildDebtScheduleKey(debt.getDebtId(), remindDaysBefore);

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);

        boolean alreadySent = notificationLogRepository
                .existsByRuleIdAndScheduleKeyAndStatusAndSentAtBetween(
                        ruleId, scheduleKey, NotificationStatus.SENT, start, end
                );

        if (alreadySent) return;

        String title = (remindDaysBefore == 0) ? "วันนี้ครบกำหนดชำระหนี้" : "เตือนหนี้ใกล้ครบกำหนด";
        String body = buildDebtBody(debt, dueDate, remindDaysBefore);

        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndIsActiveTrue(userId);

        if (devices.isEmpty()) {
            saveLog(
                    userId, ruleId, scheduleKey,
                    RefType.DEBT, String.valueOf(debt.getDebtId()),
                    NotificationChannel.PUSH, NotificationStatus.FAILED,
                    title, body, "No active device tokens"
            );
            return;
        }

        for (UserDevice d : devices) {
            try {
                Message message = Message.builder()
                        .setToken(d.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("refType", "DEBT")
                        .putData("refId", String.valueOf(debt.getDebtId()))
                        .putData("scheduleKey", scheduleKey)
                        .build();

                FirebaseMessaging.getInstance().send(message);
            } catch (Exception ex) {
                saveLog(
                        userId, ruleId, scheduleKey,
                        RefType.DEBT, String.valueOf(debt.getDebtId()),
                        NotificationChannel.PUSH, NotificationStatus.FAILED,
                        title, body, ex.getMessage()
                );
            }
        }
        saveLog(
                userId, ruleId, scheduleKey,
                RefType.DEBT, String.valueOf(debt.getDebtId()),
                NotificationChannel.PUSH, NotificationStatus.SENT,
                title, body, null
        );
    }

    // ===== Helpers =====

    private String buildDebtScheduleKey(UUID debtId, int daysBefore) {
        return "DEBT:" + debtId + ":D-" + daysBefore; // เช่น DEBT:xxxx:D-3 และ DEBT:xxxx:D-0
    }

    /**
     * รองรับ dueDay 29/30/31 โดย clamp เป็นวันสุดท้ายของเดือนถ้าเดือนนั้นไม่มีวันนั้น
     */
    public static LocalDate getDueDateForThisMonth(int dueDay, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        int lastDay = today.lengthOfMonth();
        int safeDay = Math.min(Math.max(dueDay, 1), lastDay);
        return LocalDate.of(today.getYear(), today.getMonth(), safeDay);
    }

    private String buildDebtBody(Debt debt, LocalDate dueDate, int remindDaysBefore) {
        if (remindDaysBefore == 0) {
            return "วันนี้ (" + dueDate + ") ครบกำหนดชำระหนี้: " + debt.getDebtName();
        }
        return "อีก " + remindDaysBefore + " วัน จะครบกำหนดชำระหนี้ (" + dueDate + "): " + debt.getDebtName();
    }

    private void saveLog(
            UUID userId,
            UUID ruleId,
            String scheduleKey,
            RefType refType,
            String refId,
            NotificationChannel channel,
            NotificationStatus status,
            String title,
            String body,
            String errorMessage
    ) {
        NotificationLog log = NotificationLog.builder()
                .userId(userId)
                .ruleId(ruleId)
                .scheduleKey(scheduleKey)
                .refType(refType)
                .refId(refId)
                .channel(channel)
                .status(status)
                .title(title)
                .body(body)
                .errorMessage(errorMessage)
                .sentAt(LocalDateTime.now())
                .build();
        notificationLogRepository.save(log);
    }


}

