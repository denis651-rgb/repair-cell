package com.store.repair.service;

import com.store.repair.domain.BackupSettings;

import java.nio.file.Path;

public interface RemoteBackupStorageService {

    String upload(Path file, BackupSettings settings);
}
