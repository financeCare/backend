package com.example.capstone.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A debt payment that was committed to the database. Immutable fact for the event log; consumers can rebuild
 * their own views by replaying the topic from the beginning.
 */
public record PaymentEvent(
        UUID eventId,
        UUID debtId,
        UUID userId,
        BigDecimal amount,
        LocalDate paymentDate,
        Long slipId,
        Instant occurredAt) {
}
