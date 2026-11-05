# Due-date reminders on RabbitMQ

Off by default. `NOTIFICATIONS_ASYNC_ENABLED=true` (property `app.notifications.async.enabled`) switches the reminder
scheduler from sending push notifications inline to publishing one message per reminder.

```
@Scheduled (every minute) --> reminder.exchange --reminder.debt--> reminder.debt --> DebtReminderListener
                                                        |  (3 attempts, 2 s x2 backoff, then rejected)
                                                        +--> reminder.dlx --> reminder.debt.dlq
```

- **Publisher:** `NotificationService.processRule` publishes `DebtReminderMessage(userId, ruleId, debtId, daysBefore)`
  when the rule is due in the current minute. Without the flag the old inline path runs unchanged.
- **Consumer:** `DebtReminderListener` calls `NotificationService.deliverDebtReminder`, which reloads the debt and the
  user's settings (inactive debts or disabled notifications are dropped), then runs the same delivery code as before.
- **Idempotency:** delivery is at-least-once. The consumer does not re-check the one-minute send window (a retried or
  late message would never pass it) but keeps the date check and the `SENT` de-duplication on
  `(ruleId, scheduleKey, day)`, so a redelivery does not push twice.
- **Failure:** retry settings are the shared `spring.rabbitmq.listener.simple.retry.*`; after the last attempt the
  message lands in `reminder.debt.dlq`. The DLQ listener only logs it.
- **JSON converter:** shared by both pipelines (`MessagingJsonConfig`), enabled when either flag is on.

## Verified

- Unit tests: publisher path vs inline path, consumer skips inactive debt / disabled user, de-duplication.
- `DebtReminderRabbitIntegrationTest` (Testcontainers, real RabbitMQ 3.13): message is delivered through the real
  topology; a consumer that keeps throwing is called exactly 3 times.

## Not verified

- The full Spring Boot app with the flag on against a running broker (the integration test builds the topology with
  plain Spring AMQP, not the app context), and a real FCM push (Firebase is not configured locally).
- What the DLQ should do beyond logging (alerting / manual replay) is not decided.
- A debt with `daysBefore` set to the same value as 0 would publish twice in one minute; the de-duplication covers it.
