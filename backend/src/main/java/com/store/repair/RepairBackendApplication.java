package com.store.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.repair.config.AppStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RepairBackendApplication {

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String DEFAULT_DB_URL = SQLITE_PREFIX + AppStoragePaths.resolveAppStorageDir()
            + "/data/repair-shop.db";

    public static void main(String[] args) {
        prepareRuntimeDirectories();
        SpringApplication.run(RepairBackendApplication.class, args);
    }

    private static void prepareRuntimeDirectories() {
        Path startupDatabasePath = resolveSqlitePath(resolveDatasourceUrl());

        ensureParentDirectory(startupDatabasePath);
        ensureDirectory(resolveBackupDirectory());
        ensureDirectory(resolveRestoreDirectory());

        applyPendingRestoreIfNeeded(startupDatabasePath);
    }

    private static String resolveDatasourceUrl() {
        String explicitDatasourceUrl = firstNonBlank(
                System.getProperty("spring.datasource.url"),
                System.getenv("SPRING_DATASOURCE_URL"),
                System.getProperty("DB_URL"),
                System.getenv("DB_URL"));

        if (explicitDatasourceUrl != null) {
            return explicitDatasourceUrl;
        }

        String appDbPath = firstNonBlank(
                System.getProperty("APP_DB_PATH"),
                System.getenv("APP_DB_PATH"));

        if (appDbPath != null) {
            return appDbPath.startsWith(SQLITE_PREFIX) ? appDbPath : SQLITE_PREFIX + appDbPath;
        }

        return DEFAULT_DB_URL;
    }

    private static String resolveBackupDirectory() {
        return AppStoragePaths.resolveBackupDirectory();
    }

    private static String resolveRestoreDirectory() {
        return AppStoragePaths.resolveRestoreDirectory();
    }

    private static void applyPendingRestoreIfNeeded(Path startupDatabasePath) {
        Path restoreDir = Paths.get(resolveRestoreDirectory()).toAbsolutePath().normalize();
        Path pendingPlan = restoreDir.resolve("pending-restore.json");
        Path lastResult = restoreDir.resolve("last-restore-result.json");

        if (!Files.exists(pendingPlan)) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Map<?, ?> plan = Map.of();

        try {
            Files.createDirectories(restoreDir);
            plan = objectMapper.readValue(pendingPlan.toFile(), Map.class);

            Path sourcePath = resolveRequiredPath(plan.get("sourceDatabasePath"),
                    "El plan de restauracion pendiente no tiene archivo origen.");

            if (!Files.exists(sourcePath)) {
                throw new IllegalStateException("No existe el archivo origen para restaurar: " + sourcePath);
            }

            Path targetDatabasePath = resolveTargetDatabasePath(plan, startupDatabasePath);
            if (targetDatabasePath == null) {
                throw new IllegalStateException("No se pudo resolver la base de datos destino para restaurar.");
            }

            ensureParentDirectory(targetDatabasePath);

            Path tempTarget = Paths.get(targetDatabasePath + ".restore-tmp").toAbsolutePath().normalize();
            Path rollbackTarget = Paths.get(targetDatabasePath + ".rollback").toAbsolutePath().normalize();

            String sourceType = textOrDefault(plan.get("sourceType"), "LOCAL");
            String displaySource = textOrDefault(plan.get("displaySource"), sourcePath.toString());
            String backupBeforeRestorePath = textOrDefault(plan.get("backupBeforeRestorePath"), "");

            Files.deleteIfExists(tempTarget);
            Files.deleteIfExists(rollbackTarget);

            // Importante para SQLite:
            // si quedo un WAL/SHM/JOURNAL de la base anterior, se debe eliminar antes de
            // reemplazar la DB.
            cleanupSqliteSidecarFiles(targetDatabasePath);

            Files.copy(sourcePath, tempTarget, StandardCopyOption.REPLACE_EXISTING);

            try {
                if (Files.exists(targetDatabasePath)) {
                    Files.move(targetDatabasePath, rollbackTarget, StandardCopyOption.REPLACE_EXISTING);
                }

                Files.move(tempTarget, targetDatabasePath, StandardCopyOption.REPLACE_EXISTING);

                // Limpieza defensiva posterior a la restauracion antes de iniciar Spring Boot.
                cleanupSqliteSidecarFiles(targetDatabasePath);

                Files.deleteIfExists(rollbackTarget);
                Files.deleteIfExists(pendingPlan);

                writeRestoreResult(objectMapper, lastResult, true,
                        "DRIVE".equalsIgnoreCase(sourceType)
                                ? "La restauracion desde Drive se aplico correctamente al iniciar el backend."
                                : "La restauracion local se aplico correctamente al iniciar el backend.",
                        displaySource,
                        backupBeforeRestorePath,
                        targetDatabasePath.toString());
            } catch (Exception restoreException) {
                cleanupSqliteSidecarFiles(targetDatabasePath);
                Files.deleteIfExists(tempTarget);

                if (Files.exists(rollbackTarget)) {
                    Files.deleteIfExists(targetDatabasePath);
                    Files.move(rollbackTarget, targetDatabasePath, StandardCopyOption.REPLACE_EXISTING);
                    cleanupSqliteSidecarFiles(targetDatabasePath);
                }

                throw restoreException;
            }
        } catch (Exception exception) {
            try {
                writeRestoreResult(objectMapper, lastResult, false,
                        "La restauracion local fallo al iniciar el backend: " + exception.getMessage(),
                        textOrDefault(plan.get("sourceDatabasePath"), ""),
                        textOrDefault(plan.get("backupBeforeRestorePath"), ""),
                        textOrDefault(plan.get("targetDatabasePath"), ""));

                // Evita que el backend quede bloqueado en un ciclo infinito de arranque
                // fallido.
                // La base anterior queda intacta o restaurada desde rollback si el fallo
                // ocurrio a mitad del proceso.
                Files.deleteIfExists(pendingPlan);
            } catch (IOException ignored) {
            }
        }
    }

    private static Path resolveTargetDatabasePath(Map<?, ?> plan, Path startupDatabasePath) {
        String targetFromPlan = textOrDefault(plan.get("targetDatabasePath"), "");

        if (!targetFromPlan.isBlank()) {
            return Paths.get(targetFromPlan).toAbsolutePath().normalize();
        }

        return startupDatabasePath == null ? null : startupDatabasePath.toAbsolutePath().normalize();
    }

    private static Path resolveRequiredPath(Object value, String message) {
        String text = textOrDefault(value, "");

        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            throw new IllegalStateException(message);
        }

        return Paths.get(text).toAbsolutePath().normalize();
    }

    private static void writeRestoreResult(
            ObjectMapper objectMapper,
            Path lastResult,
            boolean ok,
            String message,
            String restoredFrom,
            String backupBeforeRestorePath,
            String restoredTo) throws IOException {

        Files.createDirectories(lastResult.getParent());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("message", message);
        result.put("restoredAt", LocalDateTime.now().toString());
        result.put("restoredFrom", restoredFrom);
        result.put("backupBeforeRestorePath", backupBeforeRestorePath);
        result.put("restoredTo", restoredTo);

        objectMapper.writeValue(lastResult.toFile(), result);
    }

    private static void cleanupSqliteSidecarFiles(Path databasePath) throws IOException {
        if (databasePath == null) {
            return;
        }

        Files.deleteIfExists(Paths.get(databasePath.toString() + "-wal"));
        Files.deleteIfExists(Paths.get(databasePath.toString() + "-shm"));
        Files.deleteIfExists(Paths.get(databasePath.toString() + "-journal"));
    }

    private static Path resolveSqlitePath(String datasourceUrl) {
        if (datasourceUrl == null || !datasourceUrl.startsWith(SQLITE_PREFIX)) {
            return null;
        }

        String rawPath = datasourceUrl.substring(SQLITE_PREFIX.length()).trim();

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

    private static String textOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? fallback : text;
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
