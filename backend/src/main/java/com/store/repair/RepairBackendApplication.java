package com.store.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.repair.config.AppStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RepairBackendApplication {

    private static final String DEFAULT_DB_URL = "jdbc:sqlite:" + AppStoragePaths.resolveAppStorageDir() + "/data/repair-shop.db";

    public static void main(String[] args) {
        prepareRuntimeDirectories();
        SpringApplication.run(RepairBackendApplication.class, args);
    }

    private static void prepareRuntimeDirectories() {
        Path sqlitePath = resolveSqlitePath(resolveDatasourceUrl());
        ensureParentDirectory(sqlitePath);
        ensureDirectory(resolveBackupDirectory());
        ensureDirectory(resolveRestoreDirectory());
        applyPendingRestoreIfNeeded(sqlitePath);
    }

    private static String resolveDatasourceUrl() {
        String dbUrl = firstNonBlank(
                System.getProperty("DB_URL"),
                System.getenv("DB_URL"));

        return dbUrl == null ? DEFAULT_DB_URL : dbUrl;
    }

    private static String resolveBackupDirectory() {
        return AppStoragePaths.resolveBackupDirectory();
    }

    private static String resolveRestoreDirectory() {
        return AppStoragePaths.resolveRestoreDirectory();
    }

    private static void applyPendingRestoreIfNeeded(Path targetDatabasePath) {
        if (targetDatabasePath == null) {
            return;
        }

        Path restoreDir = Paths.get(resolveRestoreDirectory());
        Path pendingPlan = restoreDir.resolve("pending-restore.json");
        Path lastResult = restoreDir.resolve("last-restore-result.json");
        if (!Files.exists(pendingPlan)) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map<?, ?> plan = objectMapper.readValue(pendingPlan.toFile(), Map.class);
            String sourceDatabasePath = String.valueOf(plan.get("sourceDatabasePath"));
            if (sourceDatabasePath == null || sourceDatabasePath.isBlank() || "null".equals(sourceDatabasePath)) {
                throw new IllegalStateException("El plan de restauracion pendiente no tiene archivo origen.");
            }

            Path sourcePath = Paths.get(sourceDatabasePath).toAbsolutePath().normalize();
            if (!Files.exists(sourcePath)) {
                throw new IllegalStateException("No existe el archivo origen para restaurar: " + sourcePath);
            }

            Path tempTarget = Paths.get(targetDatabasePath + ".restore-tmp");
            Path rollbackTarget = Paths.get(targetDatabasePath + ".rollback");
            String sourceType = String.valueOf(plan.containsKey("sourceType") ? plan.get("sourceType") : "LOCAL");
            String displaySource = String.valueOf(plan.containsKey("displaySource")
                    ? plan.get("displaySource")
                    : sourcePath.toString());

            Files.copy(sourcePath, tempTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(targetDatabasePath)) {
                Files.move(targetDatabasePath, rollbackTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempTarget, targetDatabasePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(rollbackTarget);
            Files.deleteIfExists(pendingPlan);

            objectMapper.writeValue(lastResult.toFile(), Map.of(
                    "ok", true,
                    "message", ("DRIVE".equalsIgnoreCase(sourceType)
                            ? "La restauracion desde Drive se aplico correctamente al iniciar el backend."
                            : "La restauracion local se aplico correctamente al iniciar el backend."),
                    "restoredAt", LocalDateTime.now().toString(),
                    "restoredFrom", displaySource,
                    "backupBeforeRestorePath", String.valueOf(plan.containsKey("backupBeforeRestorePath")
                            ? plan.get("backupBeforeRestorePath")
                            : "")
            ));
        } catch (Exception exception) {
            try {
                objectMapper.writeValue(lastResult.toFile(), Map.of(
                        "ok", false,
                        "message", "La restauracion local fallo al iniciar el backend: " + exception.getMessage(),
                        "restoredAt", LocalDateTime.now().toString(),
                        "restoredFrom", "",
                        "backupBeforeRestorePath", ""
                ));
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("No se pudo aplicar la restauracion pendiente antes de iniciar el backend.", exception);
        }
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
