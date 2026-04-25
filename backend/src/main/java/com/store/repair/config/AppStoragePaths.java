package com.store.repair.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppStoragePaths {

    private static final String DEFAULT_APP_HOME = System.getProperty("user.home") + "/.taller-celular";

    private AppStoragePaths() {
    }

    public static String resolveAppStorageDir() {
        return firstNonBlank(
                System.getProperty("APP_STORAGE_DIR"),
                System.getenv("APP_STORAGE_DIR"),
                DEFAULT_APP_HOME);
    }

    public static String resolveBackupDirectory() {
        String backupDir = firstNonBlank(
                System.getProperty("APP_BACKUP_DIRECTORY"),
                System.getenv("APP_BACKUP_DIRECTORY"));

        if (backupDir != null) {
            return normalize(Paths.get(backupDir));
        }

        return normalize(Paths.get(resolveAppStorageDir(), "backups"));
    }

    public static String resolveRestoreDirectory() {
        return normalize(Paths.get(resolveAppStorageDir(), "restore"));
    }

    public static String resolveConfiguredBackupDirectory(String configuredDirectory) {
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            return resolveBackupDirectory();
        }

        Path path = Paths.get(configuredDirectory.trim());
        if (!path.isAbsolute()) {
            return resolveBackupDirectory();
        }

        return normalize(path);
    }

    private static String normalize(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
