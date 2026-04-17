package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BackupSummaryResponse {
    private final long totalBackups;
    private final long pendingUploads;
    private final String lastBackupAt;
    private final String lastBackupStatus;
    private final String lastBackupMessage;
    private final String lastRemoteLocation;
    private final String backupDirectory;
    private final boolean automaticEnabled;
    private final boolean googleDriveEnabled;
    private final boolean googleDriveReady;
    private final String nextAutomaticBackupAt;
}
