# Asynchronous slip OCR pipeline (RabbitMQ)

The synchronous `POST /slips/upload` keeps the HTTP request open while the OCR service reads the slip. When OCR is slow or
down, requests pile up and time out. `POST /slips/upload-async` stores the image, answers right away (`202`) and lets a
consumer do the OCR in the background.

```
client --POST /slips/upload-async--> API  1. store image (MinIO)
                                          2. save slip, status "queued"      (committed first)
                                          3. publish {slipId, userId, filename, contentType}
                                     <-- 202 [{id, status: "queued"}]

slip.exchange --slip.ocr--> slip.ocr queue --> consumer: read image, call OCR, fill the slip,
                                                         status "processed", apply the receiver mapping
                                   |
                                   | exception, 3 attempts (2 s, then 4 s apart)
                                   v
                       slip.dlx --> slip.ocr.dlq --> consumer: status "failed"

client --GET /slips/{id}/status--> {id, status: queued | processed | failed}
```

## Decisions

| Concern | Choice |
|---|---|
| Delivery | At-least-once (RabbitMQ). The consumer is idempotent: a slip that is no longer `queued` is skipped, a deleted slip is dropped. |
| Ordering of commit and publish | The slip row is committed before the message is published, so a fast consumer never looks for a row it cannot see. If publishing fails, the slip is marked `failed` immediately. |
| Retry | Spring AMQP listener retry: 3 attempts with exponential backoff. The message stays unacknowledged during the retries. |
| Poison messages | After the last attempt the message is rejected (not requeued) and dead-lettered to `slip.ocr.dlq`; its consumer marks the slip `failed`. The dead-letter consumer acknowledges the message after marking the slip, so nothing accumulates in the DLQ. |
| Message content | Only ids and file metadata, never the image. JSON (not Java serialization). |
| Rollout | Off by default (`SLIP_ASYNC_ENABLED=false`): no listeners, the endpoint answers `503`, the broker health check is off. The synchronous endpoint is unchanged. |
| Access control | `GET /slips/{id}/status` answers `404` for other users' slips, the same as for a missing one. |

## Configuration

| Variable | Default | |
|---|---|---|
| `SLIP_ASYNC_ENABLED` | `false` | turns the pipeline on |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5672` | |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `guest`, `guest` | override outside local development |

`docker-compose.yml` adds a `rabbitmq` service and requires `RABBITMQ_PASSWORD`.

## Verified (local, real RabbitMQ + MinIO + Postgres, mock OCR)

`loadtest/slip_e2e.py` after starting `loadtest/docker-compose.yml`, `loadtest/run-backend.sh` and `loadtest/mock_ocr.py`:

1. 3 slips uploaded: `202`, all `processed` in about 1 s.
2. OCR outage (HTTP 500): exactly 3 OCR attempts, about 7 s, then the slip is `failed` via the dead-letter queue.
3. The same message delivered again for a processed slip: no OCR call, status unchanged, queue empty.

Unit tests: `SlipAsyncPipelineTest` (enqueue, idempotency, failure, dead-letter, listener).

## Not done / limits

- Not load-tested; throughput depends on the OCR service. `prefetch=5` limits concurrent OCR calls per instance.
- A failed slip is not retried later and its message is gone once acknowledged; the user has to upload it again. (To keep failures for replay, make the DLQ consumer log and leave the message in the queue instead.)
- No Testcontainers integration test yet (the check above is a script against a real broker).
- A slip stuck in `queued` because the app crashed between commit and publish is not swept (an outbox table would fix that).
