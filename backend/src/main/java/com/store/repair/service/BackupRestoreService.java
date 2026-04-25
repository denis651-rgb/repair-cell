package com.store.repair.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.repair.config.AppStoragePaths;
import com.store.repair.domain.Auditoria;
import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.RemoteBackupFileResponse;
import com.store.repair.dto.RestoreExecutionResponse;
import com.store.repair.dto.RestoreLastResultResponse;
import com.store.repair.dto.RestoreLocalValidationResponse;
import com.store.repair.repository.AuditoriaRepository;
import com.store.repair.repository.BackupSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.boot.SpringApplication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupRestoreService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String SQLITE_HEADER = "SQLite format 3";
    private static final Set<String> REQUIRED_TABLES = Set.of(
            "usuarios",
            "clientes",
            "backup_settings",
            "backup_records",
            "ordenes_reparacion",
            "productos_inventario",
            "ventas"
    );

    private final DataSource dataSource;
    private final AuditoriaRepository auditoriaRepository;
    private final ApplicationContext applicationContext;
    private final RemoteBackupStorageService remoteBackupStorageService;
    private final BackupSettingsRepository backupSettingsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.datasource.url}")
    private String dbUrl;

    private Path restoreRoot;
    private Path restoreStagingDir;
    private Path pendingRestorePlanPath;
    private Path lastRestoreResultPath;

    @PostConstruct
    public void initializeRestorePaths() {
        restoreRoot = Paths.get(AppStoragePaths.resolveRestoreDirectory());
        restoreStagingDir = restoreRoot.resolve("staging");
        pendingRestorePlanPath = restoreRoot.resolve("pending-restore.json");
        lastRestoreResultPath = restoreRoot.resolve("last-restore-result.json");

        try {
            Files.createDirectories(restoreStagingDir);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo preparar el directorio de restauracion.", ex);
        }
    }

    public RestoreLocalValidationResponse validateLocalBackup(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Debes seleccionar un archivo .db o .zip para restaurar.");
        }

        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = getSupportedExtension(originalFileName);
        String sessionId = UUID.randomUUID().toString();
        Path sessionDir = restoreStagingDir.resolve(sessionId);
        Path uploadedArtifact = sessionDir.resolve("uploaded" + extension);

        try {
            Files.createDirectories(sessionDir);
            file.transferTo(uploadedArtifact);
            return validateStagedArtifact(sessionId, sessionDir, originalFileName, uploadedArtifact, file.getSize(), "LOCAL", originalFileName);
        } catch (BusinessException ex) {
            cleanupQuietly(sessionDir);
            throw ex;
        } catch (IOException ex) {
            cleanupQuietly(sessionDir);
            throw new BusinessException("No se pudo procesar el archivo seleccionado para restauracion.");
        }
    }

    public List<RemoteBackupFileResponse> listRemoteBackups() {
        BackupSettings settings = getDriveSettings();
        return remoteBackupStorageService.listAvailableBackups(settings).stream()
                .map(file -> RemoteBackupFileResponse.builder()
                        .fileId(file.fileId())
                        .fileName(file.fileName())
                        .sizeBytes(file.sizeBytes())
                        .createdAt(file.createdAt())
                        .modifiedAt(file.modifiedAt())
                        .build())
                .sorted(Comparator.comparing(RemoteBackupFileResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public RestoreLocalValidationResponse validateRemoteBackup(String fileId) {
        String sessionId = UUID.randomUUID().toString();
        Path sessionDir = restoreStagingDir.resolve(sessionId);

        try {
            Files.createDirectories(sessionDir);
            BackupSettings settings = getDriveSettings();
            RemoteBackupStorageService.DownloadedBackup downloadedBackup = remoteBackupStorageService
                    .downloadBackup(fileId, settings, sessionDir);
            return validateStagedArtifact(
                    sessionId,
                    sessionDir,
                    downloadedBackup.fileName(),
                    downloadedBackup.downloadedPath(),
                    downloadedBackup.sizeBytes(),
                    "DRIVE",
                    fileId
            );
        } catch (BusinessException ex) {
            cleanupQuietly(sessionDir);
            throw ex;
        } catch (RemoteBackupException ex) {
            cleanupQuietly(sessionDir);
            throw new BusinessException(ex.getMessage());
        } catch (IOException ex) {
            cleanupQuietly(sessionDir);
            throw new BusinessException("No se pudo preparar el backup remoto para restauracion.");
        }
    }

    public RestoreExecutionResponse executePreparedRestore(String sessionId) {
        if (Files.exists(pendingRestorePlanPath)) {
            throw new BusinessException("Ya hay una restauracion pendiente de aplicarse. Espera a que la app termine el proceso.");
        }

        RestoreSession session = loadSession(sessionId);
        Path sqliteSource = Paths.get(session.sqliteArtifactPath());
        if (!Files.exists(sqliteSource)) {
            throw new BusinessException("El archivo validado para restauracion ya no existe. Vuelve a seleccionar el backup.");
        }

        Path activeDbPath = resolveActiveDatabasePath();
        if (activeDbPath == null) {
            throw new BusinessException("No se pudo resolver la base de datos activa para restaurar.");
        }

        Path backupBeforeRestore = createSafetyBackup(activeDbPath);
        String displaySource = "DRIVE".equalsIgnoreCase(session.sourceType())
                ? "Google Drive: " + session.originalFileName()
                : "Archivo local: " + session.originalFileName();
        PendingRestorePlan plan = new PendingRestorePlan(
                session.sessionId(),
                sqliteSource.toString(),
                activeDbPath.toString(),
                backupBeforeRestore.toString(),
                session.sourceType(),
                displaySource,
                resolveCurrentUser(),
                LocalDateTime.now().toString()
        );

        try {
            Files.createDirectories(restoreRoot);
            objectMapper.writeValue(pendingRestorePlanPath.toFile(), plan);
            registerAudit("DRIVE".equalsIgnoreCase(session.sourceType()) ? "RESTORE_DRIVE_PREPARED" : "RESTORE_LOCAL_PREPARED",
                    "backups",
                    "Restauracion " + ("DRIVE".equalsIgnoreCase(session.sourceType()) ? "desde Drive" : "local")
                            + " preparada desde " + session.originalFileName()
                            + ". Backup previo: " + backupBeforeRestore);
        } catch (IOException ex) {
            throw new BusinessException("No se pudo preparar el plan de restauracion local.");
        }

        scheduleShutdown();

        return RestoreExecutionResponse.builder()
                .accepted(true)
                .message("La restauracion fue preparada. La app reiniciara el backend para aplicar el backup seleccionado.")
                .sessionId(sessionId)
                .backupBeforeRestorePath(backupBeforeRestore.toString())
                .restartRequired(true)
                .build();
    }

    public RestoreExecutionResponse executeLocalRestore(String sessionId) {
        return executePreparedRestore(sessionId);
    }

    public RestoreLastResultResponse getLastRestoreResult() {
        if (!Files.exists(lastRestoreResultPath)) {
            return RestoreLastResultResponse.builder()
                    .available(false)
                    .ok(false)
                    .message(null)
                    .restoredAt(null)
                    .restoredFrom(null)
                    .backupBeforeRestorePath(null)
                    .build();
        }

        try {
            RestoreResult result = objectMapper.readValue(lastRestoreResultPath.toFile(), RestoreResult.class);
            return RestoreLastResultResponse.builder()
                    .available(true)
                    .ok(result.ok())
                    .message(result.message())
                    .restoredAt(result.restoredAt())
                    .restoredFrom(result.restoredFrom())
                    .backupBeforeRestorePath(result.backupBeforeRestorePath())
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el ultimo resultado de restauracion.");
        }
    }

    private RestoreSession loadSession(String sessionId) {
        try {
            Path metadataPath = restoreStagingDir.resolve(sessionId).resolve("session.json");
            if (!Files.exists(metadataPath)) {
                throw new BusinessException("La sesion de restauracion ya no existe o expiro.");
            }
            return objectMapper.readValue(metadataPath.toFile(), RestoreSession.class);
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer la sesion de restauracion.");
        }
    }

    private void persistSession(Path metadataPath, RestoreSession session) throws IOException {
        objectMapper.writeValue(metadataPath.toFile(), session);
    }

    private RestoreLocalValidationResponse validateStagedArtifact(
            String sessionId,
            Path sessionDir,
            String originalFileName,
            Path uploadedArtifact,
            long sizeBytes,
            String sourceType,
            String sourceReference
    ) throws IOException {
        String extension = getSupportedExtension(originalFileName);

        Path sqliteArtifact;
        String detectedDatabaseFileName;
        if (".zip".equals(extension)) {
            ExtractedZip extractedZip = extractSingleDatabaseFromZip(uploadedArtifact, sessionDir);
            sqliteArtifact = extractedZip.databasePath();
            detectedDatabaseFileName = extractedZip.databaseFileName();
        } else {
            sqliteArtifact = uploadedArtifact;
            detectedDatabaseFileName = uploadedArtifact.getFileName().toString();
        }

        verifySqliteHeader(sqliteArtifact);
        verifySqliteIntegrityAndSchema(sqliteArtifact);

        RestoreSession session = new RestoreSession(
                sessionId,
                originalFileName,
                extension.substring(1).toUpperCase(Locale.ROOT),
                sizeBytes,
                uploadedArtifact.toString(),
                sqliteArtifact.toString(),
                detectedDatabaseFileName,
                sourceType,
                sourceReference,
                LocalDateTime.now().toString()
        );
        persistSession(sessionDir.resolve("session.json"), session);

        return RestoreLocalValidationResponse.builder()
                .sessionId(sessionId)
                .originalFileName(originalFileName)
                .format(session.format())
                .sizeBytes(sizeBytes)
                .detectedDatabaseFileName(detectedDatabaseFileName)
                .validatedAt(session.validatedAt())
                .message("Backup validado correctamente. La aplicacion debera reiniciar el backend para aplicar la restauracion.")
                .build();
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "backup";
        }

        String normalized = originalFileName.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String getSupportedExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".db")) {
            return ".db";
        }
        if (lower.endsWith(".zip")) {
            return ".zip";
        }
        throw new BusinessException("Solo se aceptan backups locales con extension .db o .zip.");
    }

    private ExtractedZip extractSingleDatabaseFromZip(Path zipPath, Path sessionDir) throws IOException {
        int dbCount = 0;
        String dbFileName = null;
        Path extractedDb = sessionDir.resolve("restored.db");

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw new BusinessException("El archivo ZIP contiene rutas no seguras y no puede restaurarse.");
                }

                if (entryName.toLowerCase(Locale.ROOT).endsWith(".db")) {
                    dbCount++;
                    dbFileName = Paths.get(entryName).getFileName().toString();
                    if (dbCount > 1) {
                        throw new BusinessException("El archivo ZIP debe contener exactamente una base de datos .db.");
                    }
                    Files.copy(zis, extractedDb);
                }
            }
        }

        if (dbCount != 1 || dbFileName == null || !Files.exists(extractedDb)) {
            throw new BusinessException("El archivo ZIP no contiene exactamente una base de datos valida.");
        }

        return new ExtractedZip(extractedDb, dbFileName);
    }

    private void verifySqliteHeader(Path sqlitePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(sqlitePath)) {
            byte[] header = inputStream.readNBytes(16);
            String headerText = new String(header, StandardCharsets.US_ASCII);
            if (!headerText.startsWith(SQLITE_HEADER)) {
                throw new BusinessException("El backup seleccionado no tiene un formato SQLite valido.");
            }
        }
    }

    private void verifySqliteIntegrityAndSchema(Path sqlitePath) {
        String jdbcUrl = "jdbc:sqlite:" + sqlitePath.toAbsolutePath();
        try (Connection connection = java.sql.DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {

            String integrityResult;
            try (ResultSet resultSet = statement.executeQuery("PRAGMA integrity_check")) {
                integrityResult = resultSet.next() ? resultSet.getString(1) : "";
            }

            if (!"ok".equalsIgnoreCase(integrityResult)) {
                throw new BusinessException("La base del backup no paso la validacion de integridad SQLite.");
            }

            Set<String> existingTables = new HashSet<>();
            try (ResultSet resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (resultSet.next()) {
                    existingTables.add(resultSet.getString(1).toLowerCase(Locale.ROOT));
                }
            }

            for (String requiredTable : REQUIRED_TABLES) {
                if (!existingTables.contains(requiredTable)) {
                    throw new BusinessException("El backup no contiene la estructura minima requerida del sistema. Falta la tabla: " + requiredTable);
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("No se pudo validar la base SQLite del backup seleccionado.");
        }
    }

    private Path createSafetyBackup(Path activeDbPath) {
        Path backupDir = Paths.get(AppStoragePaths.resolveBackupDirectory());
        try {
            Files.createDirectories(backupDir);
            Path backupPath = backupDir.resolve("pre-restore-" + LocalDateTime.now().format(FORMATTER) + ".db");
            String escapedPath = backupPath.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(FULL)");
                statement.execute("VACUUM INTO '" + escapedPath + "'");
            }

            if (!Files.exists(backupPath) || Files.size(backupPath) <= 0) {
                throw new BusinessException("No se pudo crear el backup de seguridad previo a la restauracion.");
            }
            return backupPath;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("No se pudo crear el backup de seguridad previo a la restauracion.");
        }
    }

    private Path resolveActiveDatabasePath() {
        String prefix = "jdbc:sqlite:";
        if (dbUrl == null || !dbUrl.startsWith(prefix)) {
            return null;
        }

        String rawPath = dbUrl.substring(prefix.length()).trim();
        if (rawPath.isBlank()) {
            return null;
        }

        String normalized = rawPath.replace('\\', '/');
        if (normalized.startsWith("file:")) {
            normalized = normalized.substring("file:".length());
        }

        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    private void scheduleShutdown() {
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }

            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }, "restore-backend-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    private void registerAudit(String action, String module, String details) {
        auditoriaRepository.save(Auditoria.builder()
                .accion(action)
                .modulo(module)
                .entidadId(null)
                .usuario(resolveCurrentUser())
                .detalles(details)
                .build());
    }

    private String resolveCurrentUser() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "Sistema";
    }

    private BackupSettings getDriveSettings() {
        BackupSettings settings = backupSettingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new BusinessException("No se encontro la configuracion de backups."));

        if (!Boolean.TRUE.equals(settings.getGoogleDriveEnabled())) {
            throw new BusinessException("Google Drive no esta habilitado en la configuracion actual.");
        }

        return settings;
    }

    private void cleanupQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record ExtractedZip(Path databasePath, String databaseFileName) {
    }

    private record RestoreSession(
            String sessionId,
            String originalFileName,
            String format,
            long sizeBytes,
            String uploadedArtifactPath,
            String sqliteArtifactPath,
            String detectedDatabaseFileName,
            String sourceType,
            String sourceReference,
            String validatedAt
    ) {
    }

    private record PendingRestorePlan(
            String sessionId,
            String sourceDatabasePath,
            String targetDatabasePath,
            String backupBeforeRestorePath,
            String sourceType,
            String displaySource,
            String requestedBy,
            String requestedAt
    ) {
    }

    private record RestoreResult(
            boolean ok,
            String message,
            String restoredAt,
            String restoredFrom,
            String backupBeforeRestorePath
    ) {
    }
}
