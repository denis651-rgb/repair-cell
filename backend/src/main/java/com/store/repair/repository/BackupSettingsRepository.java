package com.store.repair.repository;

import com.store.repair.domain.BackupSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupSettingsRepository extends JpaRepository<BackupSettings, Long> {
}
