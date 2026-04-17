package com.store.repair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RepairBackendApplication {

    private static final String DEFAULT_APP_HOME = System.getProperty("user.home") + "/.taller-celular";
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:" + DEFAULT_APP_HOME + "/data/repair-shop.db";
    private static final String DEFAULT_BACKUP_DIR = DEFAULT_APP_HOME + "/backups";

    public static void main(String[] args) {
        prepareRuntimeDirectories();
        SpringApplication.run(RepairBackendApplication.class, args);
    }

    private static void prepareRuntimeDirectories() {
        ensureParentDirectory(resolveSqlitePath(resolveDatasourceUrl()));
        ensureDirectory(resolveBackupDirectory());
    }

    private static String resolveDatasourceUrl() {
        String dbUrl = firstNonBlank(
                System.getProperty("DB_URL"),
                System.getenv("DB_URL"));

        return dbUrl == null ? DEFAULT_DB_URL : dbUrl;
    }

    private static String resolveBackupDirectory() {
        String appStorageDir = firstNonBlank(
                System.getProperty("APP_STORAGE_DIR"),
                System.getenv("APP_STORAGE_DIR"));

        String backupDir = firstNonBlank(
                System.getProperty("APP_BACKUP_DIRECTORY"),
                System.getenv("APP_BACKUP_DIRECTORY"));

        if (backupDir != null) {
            return backupDir;
        }

        if (appStorageDir != null) {
            return appStorageDir + "/backups";
        }

        return DEFAULT_BACKUP_DIR;
    }

    private static Path resolveSqlitePath(String datasourceUrl) {
        String prefix = "jdbc:sqlite:";
        if (datasourceUrl == null || !datasourceUrl.startsWith(prefix)) {
            return null;
        }

        String rawPath = datasourceUrl.substring(prefix.length()).trim();
        if (rawPath.isBlank() || ":memory:".equalsIgnoreCase(rawPath)) {
            return null;
        }

        String normalized = rawPath.replace('\\', '/');
        if (normalized.startsWith("file:")) {
            normalized = normalized.substring("file:".length());
        }

        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    private static void ensureParentDirectory(Path filePath) {
        if (filePath == null || filePath.getParent() == null) {
            return;
        }
        ensureDirectory(filePath.getParent().toString());
    }

    private static void ensureDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return;
        }

        try {
            Files.createDirectories(Paths.get(directory).toAbsolutePath().normalize());
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo crear el directorio requerido: " + directory, exception);
        }
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
