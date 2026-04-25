package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RestoreLocalValidationResponse {
    private final String sessionId;
    private final String originalFileName;
    private final String format;
    private final long sizeBytes;
    private final String detectedDatabaseFileName;
    private final String validatedAt;
    private final String message;
}
