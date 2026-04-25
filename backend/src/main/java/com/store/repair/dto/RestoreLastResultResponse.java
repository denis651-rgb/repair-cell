package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RestoreLastResultResponse {
    private final boolean available;
    private final boolean ok;
    private final String message;
    private final String restoredAt;
    private final String restoredFrom;
    private final String backupBeforeRestorePath;
}
