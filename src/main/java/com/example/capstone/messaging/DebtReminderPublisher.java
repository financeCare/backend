package com.example.capstone.messaging;

/** Hands a due-date reminder over to the asynchronous delivery pipeline. */
public interface DebtReminderPublisher {

    void publish(DebtReminderMessage message);
}
