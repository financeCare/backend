package com.example.capstone.service;

import com.example.capstone.dto.UserDeviceRequest;
import com.example.capstone.dto.UserDeviceResponse;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.NotificationLog;
import com.example.capstone.entity.NotificationRule;
import com.example.capstone.entity.UserDevice;
import com.example.capstone.entity.UserSetting;
import com.example.capstone.enums.NotificationStatus;
import com.example.capstone.enums.RefType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.NotificationLogRepository;
import com.example.capstone.repository.NotificationRuleRepository;
import com.example.capstone.repository.UserDeviceRepository;
import com.example.capstone.repository.UserSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Device registration, budget/debt notification rules, notification log handling and the due-date helpers. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceBehaviourTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

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
    private NotificationService service;

    private UUID userId;
    private final String token = "token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private void asUser() {
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
    }

    private UserDeviceRequest request(String fcm, String deviceKey) {
        UserDeviceRequest req = new UserDeviceRequest();
        req.setFcmToken(fcm);
        req.setDeviceKey(deviceKey);
        req.setPlatform("android");
        req.setDeviceName("Pixel");
        return req;
    }

    // ---------- device registration ----------

    @Test
    void register_existingFcmToken_isReassignedToTheCallerAndReactivated() {
        asUser();
        UserDevice existing = UserDevice.builder().deviceId(UUID.randomUUID()).userId(UUID.randomUUID()).fcmToken("fcm").active(false).build();
        when(userDeviceRepository.findByFcmToken("fcm")).thenReturn(Optional.of(existing));
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(i -> i.getArgument(0));

        UserDeviceResponse response = service.registerDevice(token, request("fcm", "key"));

        assertEquals(userId, existing.getUserId());
        assertTrue(existing.getActive());
        assertEquals("Pixel", existing.getDeviceName());
        assertTrue(response.isActive());
        assertNotNull(existing.getLastSeen());
    }

    @Test
    void register_knownDeviceKey_updatesTheTokenOfTheSameDevice() {
        asUser();
        UserDevice existing = UserDevice.builder().deviceId(UUID.randomUUID()).userId(userId).fcmToken("old").build();
        when(userDeviceRepository.findByFcmToken("new")).thenReturn(Optional.empty());
        when(userDeviceRepository.findByDeviceKeyAndUserId("key", userId)).thenReturn(Optional.of(existing));
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(i -> i.getArgument(0));

        service.registerDevice(token, request("new", "key"));

        assertEquals("new", existing.getFcmToken());
        verify(userDeviceRepository).save(existing);
    }

    @Test
    void register_blankTokenAndKey_createsANewActiveDevice() {
        asUser();
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(i -> i.getArgument(0));

        service.registerDevice(token, request("  ", ""));

        ArgumentCaptor<UserDevice> saved = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertTrue(saved.getValue().getActive());
        verify(userDeviceRepository, never()).findByFcmToken(any());
        verify(userDeviceRepository, never()).findByDeviceKeyAndUserId(any(), any());
    }

    @Test
    void unregister_ownDevice_isDeactivated() {
        asUser();
        UUID deviceId = UUID.randomUUID();
        UserDevice device = UserDevice.builder().deviceId(deviceId).userId(userId).active(true).build();
        when(userDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        service.unregisterDevice(token, deviceId);

        assertFalse(device.getActive());
        verify(userDeviceRepository).save(device);
    }

    @Test
    void unregister_unknownDevice_doesNothing() {
        asUser();
        when(userDeviceRepository.findById(any())).thenReturn(Optional.empty());

        service.unregisterDevice(token, UUID.randomUUID());

        verify(userDeviceRepository, never()).save(any());
    }

    // ---------- budget notifications ----------

    @Test
    void budgetRule_overTheLimit_usesTheOverspentTitleAndLogsFailureWithoutDevices() {
        UUID budgetId = UUID.randomUUID();
        when(userSettingRepository.findByUserId(userId)).thenReturn(null);
        when(userDeviceRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        service.createNotificationRuleForBudget(userId, "Food", 1000.0, 1200.0, budgetId);

        ArgumentCaptor<NotificationRule> rule = ArgumentCaptor.forClass(NotificationRule.class);
        verify(notificationRuleRepository).save(rule.capture());
        assertEquals("แจ้งเตือนงบประมาณเกินกำหนด", rule.getValue().getTitle());
        assertTrue(rule.getValue().getBodyTemplate().contains("200.00"), rule.getValue().getBodyTemplate());
        assertEquals(RefType.BUDGET, rule.getValue().getRefType());
        ArgumentCaptor<NotificationLog> logEntry = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logEntry.capture());
        assertEquals(NotificationStatus.FAILED, logEntry.getValue().getStatus());
        assertNotNull(logEntry.getValue().getSentAt());
    }

    @Test
    void budgetRule_underTheLimit_usesTheNearLimitTitleWithTheRemainingAmount() {
        when(userSettingRepository.findByUserId(userId)).thenReturn(null);
        when(userDeviceRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        service.createNotificationRuleForBudget(userId, "Food", 1000.0, 800.0, UUID.randomUUID());

        ArgumentCaptor<NotificationRule> rule = ArgumentCaptor.forClass(NotificationRule.class);
        verify(notificationRuleRepository).save(rule.capture());
        assertEquals("แจ้งเตือนงบประมาณใกล้ครบกำหนด", rule.getValue().getTitle());
        assertTrue(rule.getValue().getBodyTemplate().contains("200.00"));
        assertTrue(rule.getValue().getBodyTemplate().contains("80.00%"));
    }

    @Test
    void budgetRule_nullAndZeroLimits_doNotDivideByZero() {
        when(userSettingRepository.findByUserId(userId)).thenReturn(null);
        when(userDeviceRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        service.createNotificationRuleForBudget(userId, "Misc", null, null, UUID.randomUUID());

        ArgumentCaptor<NotificationRule> rule = ArgumentCaptor.forClass(NotificationRule.class);
        verify(notificationRuleRepository).save(rule.capture());
        assertTrue(rule.getValue().getBodyTemplate().contains("0.00%"));
    }

    @Test
    void budgetNotification_isSkippedWhenTheUserTurnedNotificationsOff() {
        UserSetting off = UserSetting.builder().userId(userId).notificationsEnabled(false).build();
        when(userSettingRepository.findByUserId(userId)).thenReturn(off);

        service.sendBudgetNotification(userId, UUID.randomUUID(), "Food", 95, 50);

        verifyNoInteractions(notificationRuleRepository, notificationLogRepository, userDeviceRepository);
    }

    @Test
    void budgetNotification_withADevice_isLoggedAsSentEvenWhenFirebaseIsNotConfigured() {
        // Documents current behaviour: without a Firebase service account nothing is pushed (the loop skips the
        // device with a warning) but the log still says SENT, so the in-app history shows a notification
        // that never left the server.
        when(userSettingRepository.findByUserId(userId)).thenReturn(null);
        when(userDeviceRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(UserDevice.builder().fcmToken("fcm").build()));

        service.sendBudgetNotification(userId, UUID.randomUUID(), "Food", 50, 500);

        ArgumentCaptor<NotificationLog> logEntry = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logEntry.capture());
        assertEquals(NotificationStatus.SENT, logEntry.getValue().getStatus());
    }

    // ---------- debt rule ----------

    @Test
    void debtRule_carriesTheDebtNameAndMinimumPayment() {
        UUID debtId = UUID.randomUUID();

        service.createNotificationRuleForDebt(userId, "Car loan", new BigDecimal("1234.5"), debtId);

        ArgumentCaptor<NotificationRule> rule = ArgumentCaptor.forClass(NotificationRule.class);
        verify(notificationRuleRepository).save(rule.capture());
        assertEquals(RefType.DEBT, rule.getValue().getRefType());
        assertEquals(debtId.toString(), rule.getValue().getRefId());
        assertEquals("แจ้งเตือนชำระหนี้: Car loan", rule.getValue().getTitle());
        assertTrue(rule.getValue().getBodyTemplate().contains("1234.50"));
        assertTrue(rule.getValue().getActive());
    }

    // ---------- logs ----------

    @Test
    void allLogs_excludeFailedOnes() {
        asUser();
        Pageable page = Pageable.unpaged();
        when(notificationLogRepository.findAllByUserIdAndStatusIsNot(userId, NotificationStatus.FAILED, page))
                .thenReturn(new PageImpl<>(List.of(new NotificationLog())));

        assertEquals(1, service.getAllNotificationLogs(token, page).getTotalElements());
    }

    @Test
    void logsByType_parseTheRefType() {
        asUser();
        Pageable page = Pageable.unpaged();
        when(notificationLogRepository.findAllByUserIdAndRefTypeAndStatusIsNot(userId, RefType.DEBT, NotificationStatus.FAILED, page))
                .thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.getNotificationLogs(token, "DEBT", page).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getNotificationLogs(token, "NOPE", page));
    }

    @Test
    void unreadCount_countsOnlySentLogs() {
        asUser();
        when(notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(
                NotificationLog.builder().status(NotificationStatus.SENT).build(),
                NotificationLog.builder().status(NotificationStatus.SENT).build(),
                NotificationLog.builder().status(NotificationStatus.CLICKED).build(),
                NotificationLog.builder().status(NotificationStatus.FAILED).build())));

        assertEquals(2, service.countUnreadNotifications(token));
    }

    @Test
    void markAllAsRead_changesOnlySentLogs() {
        asUser();
        NotificationLog sent = NotificationLog.builder().status(NotificationStatus.SENT).build();
        NotificationLog failed = NotificationLog.builder().status(NotificationStatus.FAILED).build();
        when(notificationLogRepository.findAllByUserId(userId, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(sent, failed)));

        service.markAllNotificationsAsRead(token);

        assertEquals(NotificationStatus.CLICKED, sent.getStatus());
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        verify(notificationLogRepository).saveAll(List.of(sent));
    }

    @Test
    void markClicked_ownLog_isUpdated() {
        asUser();
        UUID logId = UUID.randomUUID();
        NotificationLog mine = NotificationLog.builder().logId(logId).userId(userId).status(NotificationStatus.SENT).build();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(mine));

        service.markNotificationAsClicked(token, logId);

        assertEquals(NotificationStatus.CLICKED, mine.getStatus());
        verify(notificationLogRepository).save(mine);
    }

    @Test
    void markClicked_otherUsersLog_isForbiddenAndUntouched() {
        asUser();
        UUID logId = UUID.randomUUID();
        NotificationLog theirs = NotificationLog.builder().logId(logId).userId(UUID.randomUUID()).status(NotificationStatus.SENT).build();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(theirs));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.markNotificationAsClicked(token, logId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(NotificationStatus.SENT, theirs.getStatus());
    }

    @Test
    void markClicked_unknownLog_isNotFound() {
        asUser();
        when(notificationLogRepository.findById(any())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.markNotificationAsClicked(token, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---------- due-date helpers ----------

    @Test
    void dueDate_isClampedToTheLengthOfTheCurrentMonth() {
        LocalDate today = LocalDate.now(ZONE);

        assertEquals(today.withDayOfMonth(today.lengthOfMonth()), NotificationService.getDueDateForThisMonth(31, ZONE));
        assertEquals(today.withDayOfMonth(1), NotificationService.getDueDateForThisMonth(0, ZONE));
        assertEquals(today.withDayOfMonth(Math.min(15, today.lengthOfMonth())), NotificationService.getDueDateForThisMonth(15, ZONE));
        assertEquals(today.withDayOfMonth(today.lengthOfMonth()), NotificationService.dueDateOfThisMonth(99, ZONE));
    }

    // ---------- scheduled debt reminders (only the branches that do not depend on the wall clock) ----------

    private Debt debtDueToday() {
        Debt debt = new Debt();
        debt.setDebtId(UUID.randomUUID());
        debt.setDebtName("Card");
        debt.setDueDay(LocalDate.now(ZONE).getDayOfMonth());
        debt.setActive(true);
        return debt;
    }

    @Test
    void debtReminder_outsideTheNotifyMinute_sendsNothing() {
        LocalTime farFromNow = LocalTime.now(ZONE).plusHours(3);

        service.sendDebtNotification(userId, UUID.randomUUID(), debtDueToday(), 0, "Asia/Bangkok", farFromNow);

        verifyNoInteractions(notificationLogRepository, userDeviceRepository);
    }

    @Test
    void debtReminder_insideTheNotifyMinute_withoutDevices_logsOneFailure() {
        LocalTime now = LocalTime.now(ZONE);
        if (now.isBefore(LocalTime.of(0, 1))) {
            return;   // the notify window would straddle midnight
        }
        Debt debt = debtDueToday();
        UUID ruleId = UUID.randomUUID();
        when(notificationLogRepository.existsByRuleIdAndScheduleKeyAndStatusAndSentAtBetween(
                eq(ruleId), eq("DEBT:" + debt.getDebtId() + ":D-0"), eq(NotificationStatus.SENT), any(), any())).thenReturn(false);
        when(userDeviceRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        service.sendDebtNotification(userId, ruleId, debt, 0, null, now.minusSeconds(5));

        ArgumentCaptor<NotificationLog> logEntry = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logEntry.capture());
        assertEquals(NotificationStatus.FAILED, logEntry.getValue().getStatus());
        assertEquals("วันนี้ครบกำหนดชำระหนี้", logEntry.getValue().getTitle());
        assertEquals("DEBT:" + debt.getDebtId() + ":D-0", logEntry.getValue().getScheduleKey());
    }

    @Test
    void debtReminder_alreadySentToday_isNotSentAgain() {
        LocalTime now = LocalTime.now(ZONE);
        if (now.isBefore(LocalTime.of(0, 1))) {
            return;
        }
        when(notificationLogRepository.existsByRuleIdAndScheduleKeyAndStatusAndSentAtBetween(any(), any(), eq(NotificationStatus.SENT), any(), any()))
                .thenReturn(true);

        service.sendDebtNotification(userId, UUID.randomUUID(), debtDueToday(), 0, "Asia/Bangkok", now.minusSeconds(5));

        verify(notificationLogRepository, never()).save(any());
        verifyNoInteractions(userDeviceRepository);
    }

    @Test
    void debtReminder_forAnotherDay_isNotSentToday() {
        LocalTime now = LocalTime.now(ZONE);
        Debt debt = debtDueToday();
        debt.setDueDay(LocalDate.now(ZONE).getDayOfMonth() == 1 ? 2 : 1);   // not today, and 3 days before is not today either

        service.sendDebtNotification(userId, UUID.randomUUID(), debt, 3, "Asia/Bangkok", now.minusSeconds(5));

        verifyNoInteractions(notificationLogRepository);
    }

    @Test
    void scheduler_skipsRulesOfUsersWithNotificationsOffOrNoSettings() {
        NotificationRule offRule = NotificationRule.builder().userId(UUID.randomUUID()).refType(RefType.DEBT)
                .refId(UUID.randomUUID().toString()).build();
        NotificationRule noSettingsRule = NotificationRule.builder().userId(UUID.randomUUID()).refType(RefType.DEBT)
                .refId(UUID.randomUUID().toString()).build();
        when(notificationRuleRepository.findAllByActive(true)).thenReturn(List.of(offRule, noSettingsRule));
        when(userSettingRepository.findById(offRule.getUserId()))
                .thenReturn(Optional.of(UserSetting.builder().userId(offRule.getUserId()).notificationsEnabled(false).build()));
        when(userSettingRepository.findById(noSettingsRule.getUserId())).thenReturn(Optional.empty());

        service.runNotificationScheduler();

        verifyNoInteractions(debtRepository, notificationLogRepository);
    }

    @Test
    void scheduler_skipsInactiveOrMissingDebts() {
        UUID userA = UUID.randomUUID();
        UUID missingDebt = UUID.randomUUID();
        UUID inactiveDebt = UUID.randomUUID();
        NotificationRule missing = NotificationRule.builder().userId(userA).refType(RefType.DEBT).refId(missingDebt.toString()).build();
        NotificationRule inactive = NotificationRule.builder().userId(userA).refType(RefType.DEBT).refId(inactiveDebt.toString()).build();
        when(notificationRuleRepository.findAllByActive(true)).thenReturn(List.of(missing, inactive));
        when(userSettingRepository.findById(userA)).thenReturn(Optional.of(UserSetting.builder().userId(userA).build()));
        when(debtRepository.findById(missingDebt)).thenReturn(Optional.empty());
        Debt closed = new Debt();
        closed.setActive(false);
        when(debtRepository.findById(inactiveDebt)).thenReturn(Optional.of(closed));

        service.runNotificationScheduler();

        verifyNoInteractions(notificationLogRepository);
    }

    @Test
    void scheduler_ignoresNonDebtRules() {
        UUID userA = UUID.randomUUID();
        NotificationRule budgetRule = NotificationRule.builder().userId(userA).refType(RefType.BUDGET).refId(UUID.randomUUID().toString()).build();
        when(notificationRuleRepository.findAllByActive(true)).thenReturn(List.of(budgetRule));
        when(userSettingRepository.findById(userA)).thenReturn(Optional.of(UserSetting.builder().userId(userA).build()));

        service.runNotificationScheduler();

        verifyNoInteractions(debtRepository, notificationLogRepository);
        assertNull(budgetRule.getTitle());
    }
}
