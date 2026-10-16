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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.FirebaseMessaging;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private static final String DEFAULT_TIMEZONE = "Asia/Bangkok";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_TIMEZONE);

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
        int safeDay = Math.clamp(dueDay, 1, lastDay);
        return first.withDayOfMonth(safeDay);
    }

    @Transactional
    public UserDeviceResponse registerDevice(String token, UserDeviceRequest req) {
        UUID userId = userService.extractUserIdFromToken(token);
        LocalDateTime now = LocalDateTime.now(DEFAULT_ZONE);

        if (req.getFcmToken() != null && !req.getFcmToken().isBlank()) {
            Optional<UserDevice> byTokenOpt = userDeviceRepository.findByFcmToken(req.getFcmToken());
            if (byTokenOpt.isPresent()) {
                UserDevice d = byTokenOpt.get();
                d.setUserId(userId);
                d.setDeviceKey(req.getDeviceKey());
                d.setDeviceName(req.getDeviceName());
                d.setPlatform(req.getPlatform());
                d.setActive(true);
                d.setLastSeen(now);
                d.setUpdatedAt(now);
                UserDevice saved = userDeviceRepository.save(d);
                return toResponse(saved);
            }
        }

        if (req.getDeviceKey() != null && !req.getDeviceKey().isBlank()) {
            Optional<UserDevice> byKeyOpt = userDeviceRepository.findByDeviceKeyAndUserId(req.getDeviceKey(), userId);
            if (byKeyOpt.isPresent()) {
                UserDevice d = byKeyOpt.get();
                d.setFcmToken(req.getFcmToken());
                d.setDeviceName(req.getDeviceName());
                d.setPlatform(req.getPlatform());
                d.setActive(true);
                d.setLastSeen(now);
                d.setUpdatedAt(now);
                UserDevice saved = userDeviceRepository.save(d);
                return toResponse(saved);
            }
        }

        UserDevice d = UserDevice.builder()
                .userId(userId)
                .deviceKey(req.getDeviceKey())
                .deviceName(req.getDeviceName())
                .platform(req.getPlatform())
                .fcmToken(req.getFcmToken())
                .active(true)
                .lastSeen(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserDevice saved = userDeviceRepository.save(d);
        return toResponse(saved);
    }

    @Transactional
    public void unregisterDevice(String token, UUID deviceId) {
        UUID userId = userService.extractUserIdFromToken(token);
        Optional<UserDevice> dOpt = userDeviceRepository.findById(deviceId);
        if (dOpt.isPresent()) {
            UserDevice d = dOpt.get();
            if (d.getUserId().equals(userId)) {
                d.setActive(false);
                d.setUpdatedAt(LocalDateTime.now(DEFAULT_ZONE));
                userDeviceRepository.save(d);
            }
        }
    }

    public List<DeviceListDto> getMyDevices(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndActiveTrue(userId);
        return devices.stream()
                .map(d -> new DeviceListDto(d.getDeviceId(), d.getDeviceName(), d.getPlatform(), d.getLastSeen()))
                .toList();
    }

    private UserDeviceResponse toResponse(UserDevice d) {
        return new UserDeviceResponse(d.getDeviceId(), Boolean.TRUE.equals(d.getActive()), d.getLastSeen());
    }

    public void createNotificationRuleForBudget(UUID userId, String categoryName, Double limitBudget, Double currentAmount, UUID budgetId) {
        double limit = (limitBudget != null) ? limitBudget : 0;
        double current = (currentAmount != null) ? currentAmount : 0;
        double usedPercentage = (limit > 0) ? (current / limit) * 100 : 0;
        double remaining = limit - current;
        sendBudgetNotification(userId, budgetId, categoryName, usedPercentage, remaining);
    }

    @Transactional
    public void sendBudgetNotification(UUID userId, UUID budgetId, String categoryName, double usedPercentage, double remaining) {
        UserSetting userSetting = userSettingRepository.findByUserId(userId);
        if (userSetting != null && !Boolean.TRUE.equals(userSetting.getNotificationsEnabled())) {
            return;
        }

        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setUserId(userId);
        notificationRule.setRefType(RefType.BUDGET);
        notificationRule.setRefId(budgetId.toString());
        notificationRule.setActive(true);

        if (usedPercentage >= 100) {
            notificationRule.setTitle("แจ้งเตือนงบประมาณเกินกำหนด");
            notificationRule.setBodyTemplate(String.format(
                    "หมวด %s ใช้งบเกินแล้ว %.2f บาท (ใช้ไป %.2f%% ของงบทั้งหมด)",
                    categoryName,
                    Math.abs(remaining),
                    usedPercentage));
        } else {
            notificationRule.setTitle("แจ้งเตือนงบประมาณใกล้ครบกำหนด");
            notificationRule.setBodyTemplate(String.format(
                    "หมวด %s เหลืองบประมาณ %.2f บาท (ใช้ไปแล้ว %.2f%% ของงบทั้งหมด)",
                    categoryName,
                    remaining,
                    usedPercentage));
        }
        notificationRuleRepository.save(notificationRule);
        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            saveLogDirect(NotificationLog.builder()
                    .userId(userId)
                    .ruleId(notificationRule.getRuleId())
                    .refType(RefType.BUDGET)
                    .refId(budgetId.toString())
                    .channel(NotificationChannel.PUSH)
                    .status(NotificationStatus.FAILED)
                    .title(notificationRule.getTitle())
                    .body(notificationRule.getBodyTemplate())
                    .errorMessage("this user doesn't have any device")
                    .build());
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
                        .putData("refType", "BUDGET")
                        .putData("refId", budgetId.toString())
                        .build();
                if (FirebaseApp.getApps().isEmpty()) {
                    log.warn("Firebase is not initialized. Skipping send.");
                    continue;
                }
                FirebaseMessaging.getInstance().send(message);
            } catch (Exception ex) {
                saveLogDirect(NotificationLog.builder()
                        .userId(userId)
                        .ruleId(notificationRule.getRuleId())
                        .refType(RefType.BUDGET)
                        .refId(budgetId.toString())
                        .channel(NotificationChannel.PUSH)
                        .status(NotificationStatus.FAILED)
                        .title(notificationRule.getTitle())
                        .body(notificationRule.getBodyTemplate())
                        .errorMessage(ex.getMessage())
                        .build());
            }
        }
        saveLogDirect(NotificationLog.builder()
                .userId(userId)
                .ruleId(notificationRule.getRuleId())
                .refType(RefType.BUDGET)
                .refId(budgetId.toString())
                .channel(NotificationChannel.PUSH)
                .status(NotificationStatus.SENT)
                .title(notificationRule.getTitle())
                .body(notificationRule.getBodyTemplate())
                .build());
    }

    public void createNotificationRuleForDebt(UUID userId, String debtName, BigDecimal minPayment, UUID debtId) {
        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setUserId(userId);
        notificationRule.setRefType(RefType.DEBT);
        notificationRule.setRefId(debtId.toString());
        notificationRule.setActive(true);
        notificationRule.setTitle("แจ้งเตือนชำระหนี้: " + debtName);
        notificationRule.setBodyTemplate(String.format("ยอดชำระขั้นต่ำ %.2f บาท สำหรับ %s", minPayment, debtName));
        notificationRuleRepository.save(notificationRule);
    }

    public Page<NotificationLog> getAllNotificationLogs(String token, Pageable pageable) {
        UUID userId = userService.extractUserIdFromToken(token);
        return notificationLogRepository.findAllByUserIdAndStatusIsNot(userId, NotificationStatus.FAILED, pageable);
    }

    public Page<NotificationLog> getNotificationLogs(String token, String refType, Pageable pageable) {
        UUID userId = userService.extractUserIdFromToken(token);
        return notificationLogRepository.findAllByUserIdAndRefTypeAndStatusIsNot(userId, RefType.valueOf(refType), NotificationStatus.FAILED, pageable);
    }

    public long countUnreadNotifications(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        return notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())
                .stream()
                .filter(logEntry -> logEntry.getStatus() == NotificationStatus.SENT)
                .count();
    }

    @Transactional
    public void markAllNotificationsAsRead(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        List<NotificationLog> logs = notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())
                .stream()
                .filter(logEntry -> logEntry.getStatus() == NotificationStatus.SENT)
                .toList();
        for (NotificationLog l : logs) {
            l.setStatus(NotificationStatus.CLICKED);
        }
        notificationLogRepository.saveAll(logs);
    }

    @Transactional
    public void markNotificationAsClicked(String token, UUID logId) {
        UUID userId = userService.extractUserIdFromToken(token);
        NotificationLog logEntry = notificationLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException("Notification not found", HttpStatus.NOT_FOUND));
        if (!logEntry.getUserId().equals(userId)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        logEntry.setStatus(NotificationStatus.CLICKED);
        notificationLogRepository.save(logEntry);
    }

    @Scheduled(cron = "0 * * * * *")
    public void runNotificationScheduler() {
        List<NotificationRule> rules = notificationRuleRepository.findAllByActive(true);
        for (NotificationRule rule : rules) {
            processRule(rule);
        }
    }

    private void processRule(NotificationRule rule) {
        UUID userId = rule.getUserId();

        UserSetting setting = userSettingRepository.findById(userId).orElse(null);
        if (setting == null || !Boolean.TRUE.equals(setting.getNotificationsEnabled())) {
            return;
        }

        if (rule.getRefType() == RefType.DEBT) {
            Debt debt = debtRepository
                    .findById(UUID.fromString(rule.getRefId()))
                    .orElse(null);
            if (debt == null || !debt.getActive()) {
                return;
            }
            String timezone = setting.getTimezone();
            LocalTime timeOfDay = setting.getDefaultNotifyTime();
            int remindDaysBefore = setting.getDefaultRemindDaysBefore();
            int[] daysList = new int[] { remindDaysBefore, 0 };

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
            String timezone) {
        ZoneId zoneId = ZoneId.of((timezone == null || timezone.isBlank()) ? DEFAULT_TIMEZONE : timezone);

        LocalDate today = LocalDate.now(zoneId);
        LocalTime nowTime = LocalTime.now(zoneId);

        LocalDate dueDate = getDueDateForThisMonth(dueDay, zoneId);
        LocalDate notifyDate = dueDate.minusDays(remindDaysBefore);

        if (!today.equals(notifyDate)) {
            return false;
        }

        LocalTime notifyTime = (timeOfDay != null) ? timeOfDay : LocalTime.of(9, 0);

        return !nowTime.isBefore(notifyTime) && nowTime.isBefore(notifyTime.plusMinutes(1));
    }

    @Transactional
    public void sendDebtNotification(
            UUID userId,
            UUID ruleId,
            Debt debt,
            int remindDaysBefore,
            String timezone,
            LocalTime timeOfDay) {
        ZoneId zoneId = ZoneId.of((timezone == null || timezone.isBlank()) ? DEFAULT_TIMEZONE : timezone);

        LocalDate today = LocalDate.now(zoneId);
        LocalTime nowTime = LocalTime.now(zoneId);
        LocalTime notifyTime = (timeOfDay != null) ? timeOfDay : LocalTime.of(9, 0);

        if (nowTime.isBefore(notifyTime) || !nowTime.isBefore(notifyTime.plusMinutes(1))) {
            return;
        }

        LocalDate dueDate = getDueDateForThisMonth(debt.getDueDay(), zoneId);
        LocalDate notifyDate = dueDate.minusDays(remindDaysBefore);

        if (!today.equals(notifyDate))
            return;

        String scheduleKey = buildDebtScheduleKey(debt.getDebtId(), remindDaysBefore);

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);

        boolean alreadySent = notificationLogRepository
                .existsByRuleIdAndScheduleKeyAndStatusAndSentAtBetween(
                        ruleId, scheduleKey, NotificationStatus.SENT, start, end);

        if (alreadySent)
            return;

        String title = (remindDaysBefore == 0) ? "วันนี้ครบกำหนดชำระหนี้" : "เตือนหนี้ใกล้ครบกำหนด";
        String body = buildDebtBody(debt, dueDate, remindDaysBefore);

        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndActiveTrue(userId);

        if (devices.isEmpty()) {
            saveLogDirect(NotificationLog.builder()
                    .userId(userId)
                    .ruleId(ruleId)
                    .scheduleKey(scheduleKey)
                    .refType(RefType.DEBT)
                    .refId(String.valueOf(debt.getDebtId()))
                    .channel(NotificationChannel.PUSH)
                    .status(NotificationStatus.FAILED)
                    .title(title)
                    .body(body)
                    .errorMessage("No active device tokens")
                    .build());
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
                if (FirebaseApp.getApps().isEmpty()) {
                    log.warn("Firebase is not initialized. Skipping send.");
                    continue;
                }
                FirebaseMessaging.getInstance().send(message);
            } catch (Exception ex) {
                saveLogDirect(NotificationLog.builder()
                        .userId(userId)
                        .ruleId(ruleId)
                        .scheduleKey(scheduleKey)
                        .refType(RefType.DEBT)
                        .refId(String.valueOf(debt.getDebtId()))
                        .channel(NotificationChannel.PUSH)
                        .status(NotificationStatus.FAILED)
                        .title(title)
                        .body(body)
                        .errorMessage(ex.getMessage())
                        .build());
            }
        }
        saveLogDirect(NotificationLog.builder()
                .userId(userId)
                .ruleId(ruleId)
                .scheduleKey(scheduleKey)
                .refType(RefType.DEBT)
                .refId(String.valueOf(debt.getDebtId()))
                .channel(NotificationChannel.PUSH)
                .status(NotificationStatus.SENT)
                .title(title)
                .body(body)
                .build());
    }

    private String buildDebtScheduleKey(UUID debtId, int daysBefore) {
        return "DEBT:" + debtId + ":D-" + daysBefore;
    }

    public static LocalDate getDueDateForThisMonth(int dueDay, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        int lastDay = today.lengthOfMonth();
        int safeDay = Math.clamp(dueDay, 1, lastDay);
        return LocalDate.of(today.getYear(), today.getMonth(), safeDay);
    }

    private String buildDebtBody(Debt debt, LocalDate dueDate, int remindDaysBefore) {
        if (remindDaysBefore == 0) {
            return "วันนี้ (" + dueDate + ") ครบกำหนดชำระหนี้: " + debt.getDebtName();
        }
        return "อีก " + remindDaysBefore + " วัน จะครบกำหนดชำระหนี้ (" + dueDate + "): " + debt.getDebtName();
    }

    private void saveLogDirect(NotificationLog notificationLog) {
        if (notificationLog.getSentAt() == null) {
            notificationLog.setSentAt(LocalDateTime.now(DEFAULT_ZONE));
        }
        notificationLogRepository.save(notificationLog);
    }
}
