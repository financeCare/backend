package com.example.capstone.messaging;

import java.util.UUID;

/**
 * Work item for the asynchronous slip OCR pipeline. It only carries ids and file metadata:
 * the image itself stays in object storage and the result is written back to the slip row.
 */
public record SlipOcrMessage(Long slipId, UUID userId, String filename, String contentType) {
}
