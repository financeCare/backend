# Payment event log on Kafka (experiment)

Off by default. With `KAFKA_EVENTS_ENABLED=true` (property `app.events.kafka.enabled`) every committed debt payment is
appended to the Kafka topic `payment-events`. Nothing in the API reads from it; it exists to try the "log" model next to
the RabbitMQ "queue" model the app already uses.

```
RepaymentService.processDebtPayment --(after the DB commit)--> KafkaPaymentEventPublisher
        --> topic payment-events (key = debtId, 3 partitions in the test) --> PaymentEventReplayer (read model)
```

- **Event:** `PaymentEvent(eventId, debtId, userId, amount, paymentDate, slipId, occurredAt)`, JSON.
- **After commit:** the event is registered as a transaction synchronization, so a rolled-back payment never reaches
  the log. The reverse gap exists: if the app dies between the commit and the send, the event is lost (no outbox).
- **Key = debt id:** all events of one debt land on one partition and stay in order.
- **Publish failures** are logged, never thrown: the payment is the source of truth, the log is secondary.
- **Replay:** `PaymentEventReplayer.replayTotals()` reads the whole topic from the first offset and builds total paid
  per debt, ignoring duplicate `eventId`s. Replaying twice gives the same answer.

## Run it locally

```bash
docker compose --profile kafka up -d kafka          # single node, KRaft, no ZooKeeper
KAFKA_EVENTS_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=kafka:9092   # on the app container
```

The broker publishes no host port (same as RabbitMQ in this compose file). Auto topic creation is on, so the first
payment creates `payment-events` with the broker defaults (1 partition).

## RabbitMQ or Kafka for this app

| | RabbitMQ (slips, reminders) | Kafka (payment events) |
|---|---|---|
| Model | work queue: a message is handed to one consumer and removed once acknowledged | append-only log: consumers track their own offset, records stay until retention |
| Good for | "do this job once, retry on failure, dead-letter when it keeps failing" | "keep the history, let several readers rebuild their own view later" |
| Failure handling | per-message retry, DLQ | consumer decides; a bad record needs an explicit skip or dead-letter topic |
| Ordering | per queue | per partition (hence the debt id key) |
| Cost here | one container, already used | one more container; extra operational weight for one producer |

For OCR jobs and reminders the queue is the right tool: nobody needs yesterday's job again. For payments an
append-only history is useful (audit, recomputing balances, a future analytics consumer), but at this size the
database already holds that history in `debt_transactions`. Kafka would only pay off once a second consumer
needs the stream. Treat this as an experiment, not a recommendation to run Kafka in production for this project.

## Verified

- `PaymentEventKafkaIntegrationTest` (Testcontainers, real `apache/kafka:3.8.0`, KRaft): three events plus one
  duplicate delivery are appended, two replays both return 150.25 for one debt and 300.00 for the other.
- `docker compose --profile kafka config` parses; the broker was not started from the compose file.

## Not verified

- The whole Spring Boot app with the flag on (the test builds the publisher by hand), and the compose broker with the
  app container talking to it.
- No outbox, no schema registry, no consumer group in the app, no retention or partition sizing, no security (plaintext).
