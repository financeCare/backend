package com.example.capstone.messaging;

/** Appends committed payments to the payment event log. */
public interface PaymentEventPublisher {

    void publish(PaymentEvent event);
}
