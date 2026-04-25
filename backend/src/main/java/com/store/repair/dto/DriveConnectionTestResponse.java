package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DriveConnectionTestResponse {
    private final boolean ok;
    private final boolean retryable;
    private final String message;
    private final String folderId;
    private final String folderName;
    private final String checkedAt;
}
