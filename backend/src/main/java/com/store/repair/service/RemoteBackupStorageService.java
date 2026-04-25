package com.store.repair.service;

import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.DriveConnectionTestResponse;

import java.nio.file.Path;
import java.util.List;

public interface RemoteBackupStorageService {

    String upload(Path file, BackupSettings settings);

    DriveConnectionTestResponse testConnection(BackupSettings settings);

    List<RemoteBackupFileDescriptor> listAvailableBackups(BackupSettings settings);

    DownloadedBackup downloadBackup(String fileId, BackupSettings settings, Path targetDirectory);

    record RemoteBackupFileDescriptor(
            String fileId,
            String fileName,
            long sizeBytes,
            String createdAt,
            String modifiedAt
    ) {
    }

    record DownloadedBackup(
            String fileId,
            String fileName,
            long sizeBytes,
            String createdAt,
            Path downloadedPath
    ) {
    }
}
