package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BackupSettingsResponse {
    private final boolean enabled;
    private final String cron;
    private final String directory;
    private final boolean zipEnabled;
    private final int retentionDays;
    private final boolean googleDriveEnabled;
    private final String googleDriveFolderId;
    private final String googleServiceAccountKeyPath;
    private final boolean googleDriveReady;
    private final String lastAutomaticBackupAt;
    private final String nextAutomaticBackupAt;
}
