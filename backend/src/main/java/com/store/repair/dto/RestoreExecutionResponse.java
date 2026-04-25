package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RestoreExecutionResponse {
    private final boolean accepted;
    private final String message;
    private final String sessionId;
    private final String backupBeforeRestorePath;
    private final boolean restartRequired;
}
