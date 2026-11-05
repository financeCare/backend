package com.example.capstone.messaging;

/** Hands a slip over to the asynchronous OCR pipeline. */
public interface SlipOcrPublisher {

    void publish(SlipOcrMessage message);
}
