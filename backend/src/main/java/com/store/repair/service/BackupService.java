package com.store.repair.service;

import com.store.repair.config.AppStoragePaths;
import com.store.repair.config.BackupProperties;
import com.store.repair.domain.BackupEstado;
import com.store.repair.domain.BackupOrigen;
import com.store.repair.domain.BackupRecord;
import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.BackupResponse;
import com.store.repair.dto.BackupSettingsRequest;
import com.store.repair.dto.BackupSettingsResponse;
import com.store.repair.dto.BackupSummaryResponse;
import com.store.repair.dto.DriveConnectionTestResponse;
import com.store.repair.dto.GoogleOAuthStartResponse;
import com.store.repair.repository.BackupRecordRepository;
import com.store.repair.repository.BackupSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_REMOTE_UPLOAD_ATTEMPTS = 5;

    private final DataSource dataSource;
    private final BackupProperties backupProperties;
    private final BackupArtifactVerifier backupArtifactVerifier;
    private final RemoteBackupStorageService remoteBackupStorageService;
    private final GoogleDriveBackupStorageService googleDriveBackupStorageService;
    private final BackupRecordRepository backupRecordRepository;
    private final BackupSettingsRepository backupSettingsRepository;

    private final ReentrantLock backupLock = new ReentrantLock();

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @PostConstruct
    public void initializeSettings() {
        if (dbUrl == null || !dbUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalStateException("BackupService requiere SQLite con spring.datasource.url=jdbc:sqlite:...");
        }

        backupSettingsRepository.findAll().stream().findFirst().orElseGet(() -> backupSettingsRepository.save(
                BackupSettings.builder()
                        .enabled(backupProperties.isEnabled())
                        .cron(backupProperties.getCron())
                        .directory(AppStoragePaths.resolveConfiguredBackupDirectory(backupProperties.getDirectory()))
                        .zipEnabled(backupProperties.isZipEnabled())
                        .retentionDays(backupProperties.getRetentionDays())
                        .googleDriveEnabled(backupProperties.isGoogleDriveEnabled())
                        .googleDriveFolderId(safeTrim(backupProperties.getGoogleDriveFolderId()))
                        .googleDriveFolderName(null)
                        .googleServiceAccountKeyPath(null)
                        .googleOauthClientId(null)
                        .googleOauthClientSecret(null)
                        .googleOauthRefreshToken(null)
                        .build()));

        BackupSettings settings = getSettingsEntity();
        String normalizedDirectory = AppStoragePaths.resolveConfiguredBackupDirectory(settings.getDirectory());
        if (!normalizedDirectory.equals(settings.getDirectory())) {
            settings.setDirectory(normalizedDirectory);
            backupSettingsRepository.save(settings);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void scheduleBackup() {
        BackupSettings settings = getSettingsEntity();
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return;
        }

        try {
            CronExpression cronExpression = CronExpression.parse(settings.getCron());
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            LocalDateTime reference = settings.getLastAutomaticBackupAt() != null
                    ? settings.getLastAutomaticBackupAt()
                    : now.minusDays(1);

            LocalDateTime nextExecution = cronExpression.next(reference);
            if (nextExecution != null && !nextExecution.isAfter(now)) {
                BackupResponse response = performBackupInterno(BackupOrigen.PROGRAMADO);
                settings.setLastAutomaticBackupAt(LocalDateTime.now());
                backupSettingsRepository.save(settings);
                log.info("Backup programado completado: {}", response.getRutaLocal());
            }
        } catch (Exception ex) {
            log.error("Error en backup programado: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void retryPendingScheduled() {
        try {
            retryPendingUploads();
        } catch (Exception ex) {
            log.warn("No se pudo reintentar la subida de backups pendientes: {}", ex.getMessage());
        }
    }

    public BackupResponse performManualBackup() {
        return performBackupInterno(BackupOrigen.MANUAL);
    }

    public Page<BackupRecord> listBackups(int pagina, int tamano) {
        return backupRecordRepository.findAllByOrderByGeneradoEnDesc(PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public BackupSettingsResponse getSettings() {
        BackupSettings settings = getSettingsEntity();
        LocalDateTime nextAutomaticBackupAt = resolveNextAutomaticBackupAt(settings);

        return BackupSettingsResponse.builder()
                .enabled(Boolean.TRUE.equals(settings.getEnabled()))
                .cron(settings.getCron())
                .directory(settings.getDirectory())
                .zipEnabled(Boolean.TRUE.equals(settings.getZipEnabled()))
                .retentionDays(settings.getRetentionDays())
                .googleDriveEnabled(Boolean.TRUE.equals(settings.getGoogleDriveEnabled()))
                .googleDriveFolderId(settings.getGoogleDriveFolderId())
                .googleDriveFolderName(settings.getGoogleDriveFolderName())
                .googleServiceAccountKeyPath(settings.getGoogleServiceAccountKeyPath())
                .googleOauthClientId(settings.getGoogleOauthClientId())
                .googleOauthClientSecret(settings.getGoogleOauthClientSecret())
                .googleOauthConnected(settings.getGoogleOauthRefreshToken() != null && !settings.getGoogleOauthRefreshToken().isBlank())
                .googleOauthConnectedAt(settings.getGoogleOauthConnectedAt() == null ? null : settings.getGoogleOauthConnectedAt().toString())
                .googleDriveReady(isGoogleDriveReady(settings))
                .lastAutomaticBackupAt(settings.getLastAutomaticBackupAt() == null ? null : settings.getLastAutomaticBackupAt().toString())
                .nextAutomaticBackupAt(nextAutomaticBackupAt == null ? null : nextAutomaticBackupAt.toString())
                .build();
    }

    public BackupSettingsResponse updateSettings(BackupSettingsRequest request) {
        String normalizedDirectory = validateAndNormalizeDirectory(request.getDirectory());
        validateRequest(request);

        BackupSettings settings = getSettingsEntity();
        settings.setEnabled(request.isEnabled());
        settings.setCron(request.getCron().trim());
        settings.setDirectory(normalizedDirectory);
        settings.setZipEnabled(request.isZipEnabled());
        settings.setRetentionDays(request.getRetentionDays());
        settings.setGoogleDriveEnabled(request.isGoogleDriveEnabled());

        String nextOauthClientId = safeTrim(request.getGoogleOauthClientId());
        String nextOauthClientSecret = safeTrim(request.getGoogleOauthClientSecret());
        boolean oauthClientChanged = !safeEquals(settings.getGoogleOauthClientId(), nextOauthClientId);
        boolean oauthClientSecretChanged = !safeEquals(settings.getGoogleOauthClientSecret(), nextOauthClientSecret);
        settings.setGoogleOauthClientId(nextOauthClientId);
        settings.setGoogleOauthClientSecret(nextOauthClientSecret);
        settings.setGoogleServiceAccountKeyPath(null);

        if (!request.isGoogleDriveEnabled() || oauthClientChanged || oauthClientSecretChanged) {
            settings.setGoogleOauthRefreshToken(null);
            settings.setGoogleOauthConnectedAt(null);
            settings.setGoogleDriveFolderId(null);
            settings.setGoogleDriveFolderName(null);
        }

        backupSettingsRepository.save(settings);
        return getSettings();
    }

    public BackupSummaryResponse getSummary() {
        BackupSettings settings = getSettingsEntity();
        BackupRecord lastRecord = backupRecordRepository.findTopByOrderByGeneradoEnDesc().orElse(null);
        LocalDateTime nextAutomaticBackupAt = resolveNextAutomaticBackupAt(settings);

        return BackupSummaryResponse.builder()
                .totalBackups(backupRecordRepository.count())
                .pendingUploads(backupRecordRepository.countByEstado(BackupEstado.PENDING_UPLOAD))
                .lastBackupAt(lastRecord == null ? null : lastRecord.getGeneradoEn().toString())
                .lastBackupStatus(lastRecord == null ? null : lastRecord.getEstado().name())
                .lastBackupMessage(lastRecord == null ? null : lastRecord.getMensaje())
                .lastRemoteLocation(lastRecord == null ? null : lastRecord.getUbicacionRemota())
                .backupDirectory(settings.getDirectory())
                .automaticEnabled(Boolean.TRUE.equals(settings.getEnabled()))
                .googleDriveEnabled(Boolean.TRUE.equals(settings.getGoogleDriveEnabled()))
                .googleDriveReady(isGoogleDriveReady(settings))
                .nextAutomaticBackupAt(nextAutomaticBackupAt == null ? null : nextAutomaticBackupAt.toString())
                .build();
    }

    public int retryPendingUploads() {
        BackupSettings settings = getSettingsEntity();
        if (!Boolean.TRUE.equals(settings.getGoogleDriveEnabled())) {
            return 0;
        }

        int retried = 0;
        for (BackupRecord record : backupRecordRepository.findTop20ByEstadoOrderByGeneradoEnAsc(BackupEstado.PENDING_UPLOAD)) {
            Path file = Paths.get(record.getRutaLocal());
            if (!Files.exists(file)) {
                record.setEstado(BackupEstado.FAILED_UPLOAD);
                record.setMensaje("El archivo local ya no existe para reintentar la subida");
                backupRecordRepository.save(record);
                continue;
            }

            try {
                String remoteLocation = remoteBackupStorageService.upload(file, settings);
                record.setEstado(BackupEstado.REMOTE_OK);
                record.setUbicacionRemota(remoteLocation);
                record.setMensaje("Subido correctamente a Google Drive");
                record.setUltimoIntentoSubidaEn(LocalDateTime.now());
                record.setIntentosSubida(record.getIntentosSubida() + 1);
                backupRecordRepository.save(record);
                retried++;
            } catch (RemoteBackupException ex) {
                markRemoteUploadFailure(record, ex);
                record.setUltimoIntentoSubidaEn(LocalDateTime.now());
                backupRecordRepository.save(record);
            }
        }

        return retried;
    }

    public DriveConnectionTestResponse testDriveConnection() {
        return remoteBackupStorageService.testConnection(getSettingsEntity());
    }

    public GoogleOAuthStartResponse startGoogleDriveOAuth(BackupSettingsRequest request) {
        String normalizedDirectory = validateAndNormalizeDirectory(request.getDirectory());
        validateRequest(request);

        if (request.getGoogleOauthClientId() == null || request.getGoogleOauthClientId().isBlank()) {
            throw new BusinessException("Debes indicar el Client ID de Google OAuth.");
        }

        BackupSettings settings = getSettingsEntity();
        settings.setEnabled(request.isEnabled());
        settings.setCron(request.getCron().trim());
        settings.setDirectory(normalizedDirectory);
        settings.setZipEnabled(request.isZipEnabled());
        settings.setRetentionDays(request.getRetentionDays());
        settings.setGoogleDriveEnabled(request.isGoogleDriveEnabled());

        String nextOauthClientId = safeTrim(request.getGoogleOauthClientId());
        String nextOauthClientSecret = safeTrim(request.getGoogleOauthClientSecret());
        boolean oauthClientChanged = !safeEquals(settings.getGoogleOauthClientId(), nextOauthClientId);
        boolean oauthClientSecretChanged = !safeEquals(settings.getGoogleOauthClientSecret(), nextOauthClientSecret);
        settings.setGoogleOauthClientId(nextOauthClientId);
        settings.setGoogleOauthClientSecret(nextOauthClientSecret);
        settings.setGoogleServiceAccountKeyPath(null);
        if (oauthClientChanged || oauthClientSecretChanged) {
            settings.setGoogleOauthRefreshToken(null);
            settings.setGoogleOauthConnectedAt(null);
            settings.setGoogleDriveFolderId(null);
            settings.setGoogleDriveFolderName(null);
        }

        backupSettingsRepository.save(settings);
        return googleDriveBackupStorageService.startAuthorization(settings);
    }

    public String completeGoogleDriveOAuth(String state, String code, String error, String errorDescription) {
        return googleDriveBackupStorageService.completeAuthorization(state, code, error, errorDescription);
    }

    public void disconnectGoogleDrive() {
        googleDriveBackupStorageService.disconnect(getSettingsEntity());
    }

    private BackupResponse performBackupInterno(BackupOrigen origen) {
        if (!backupLock.tryLock()) {
            throw new IllegalStateException("Ya hay un backup en ejecucion");
        }

        try {
            BackupSettings settings = getSettingsEntity();
            Path backupDir = Paths.get(settings.getDirectory()).toAbsolutePath().normalize();
            ensureBackupDirectoryWritable(backupDir);

            String timestamp = LocalDateTime.now().format(FORMATTER);
            String baseName = "repair-backup-" + timestamp;
            Path tempBackupDb = backupDir.resolve(baseName + ".db");

            ejecutarVacuumInto(tempBackupDb);

            Path finalArtifact = tempBackupDb;
            if (Boolean.TRUE.equals(settings.getZipEnabled())) {
                finalArtifact = comprimirZip(tempBackupDb);
                Files.deleteIfExists(tempBackupDb);
            }

            backupArtifactVerifier.verify(finalArtifact, Boolean.TRUE.equals(settings.getZipEnabled()));

            limpiarBackupsAntiguos(backupDir, settings.getRetentionDays());

            BackupRecord record = BackupRecord.builder()
                    .archivo(finalArtifact.getFileName().toString())
                    .rutaLocal(finalArtifact.toString())
                    .generadoEn(LocalDateTime.now())
                    .origen(origen)
                    .estado(BackupEstado.LOCAL_OK)
                    .tamanoBytes(Files.size(finalArtifact))
                    .mensaje("Backup local generado correctamente")
                    .build();

            if (Boolean.TRUE.equals(settings.getGoogleDriveEnabled())) {
                try {
                    String remoteLocation = remoteBackupStorageService.upload(finalArtifact, settings);
                    record.setEstado(BackupEstado.REMOTE_OK);
                    record.setUbicacionRemota(remoteLocation);
                    record.setUltimoIntentoSubidaEn(LocalDateTime.now());
                    record.setIntentosSubida(1);
                    record.setMensaje("Backup generado y subido a Google Drive");
                } catch (RemoteBackupException ex) {
                    markRemoteUploadFailure(record, ex);
                    record.setUltimoIntentoSubidaEn(LocalDateTime.now());
                    log.warn("El backup local fue creado, pero la subida remota fallo: {}", ex.getMessage());
                }
            }

            BackupRecord savedRecord = backupRecordRepository.save(record);

            return BackupResponse.builder()
                    .ok(true)
                    .mensaje(savedRecord.getMensaje())
                    .archivo(savedRecord.getArchivo())
                    .rutaLocal(savedRecord.getRutaLocal())
                    .generadoEn(savedRecord.getGeneradoEn().toString())
                    .ubicacionRemota(savedRecord.getUbicacionRemota())
                    .estado(savedRecord.getEstado().name())
                    .origen(savedRecord.getOrigen().name())
                    .build();

        } catch (IOException ex) {
            throw new BusinessException(resolveBackupIoMessage(ex, "No se pudo crear el backup local."));
        } finally {
            backupLock.unlock();
        }
    }

    private BackupSettings getSettingsEntity() {
        return backupSettingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontro la configuracion de backups"));
    }

    private void validateRequest(BackupSettingsRequest request) {
        if (request.getCron() == null || request.getCron().isBlank()) {
            throw new BusinessException("La expresion cron es obligatoria");
        }

        try {
            CronExpression.parse(request.getCron().trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("La expresion cron no es valida");
        }

        if (request.getDirectory() == null || request.getDirectory().isBlank()) {
            throw new BusinessException("La carpeta local es obligatoria");
        }

        if (request.getRetentionDays() < 1) {
            throw new BusinessException("La retencion minima es de 1 dia");
        }
    }

    private void ejecutarVacuumInto(Path backupFile) {
        String escapedPath = backupFile.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
        String sql = "VACUUM INTO '" + escapedPath + "'";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA wal_checkpoint(FULL)");
            statement.execute(sql);

        } catch (Exception ex) {
            throw new BusinessException("No se pudo generar la copia local de la base SQLite.");
        }
    }

    private Path comprimirZip(Path sourceFile) {
        Path zipPath = Paths.get(sourceFile.toString().replaceAll("\\.db$", ".zip"));

        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            ZipEntry zipEntry = new ZipEntry(sourceFile.getFileName().toString());
            zos.putNextEntry(zipEntry);
            Files.copy(sourceFile, zos);
            zos.closeEntry();

            return zipPath;
        } catch (IOException ex) {
            throw new BusinessException(resolveBackupIoMessage(ex, "No se pudo comprimir el backup local."));
        }
    }

    private void limpiarBackupsAntiguos(Path backupDir, int retentionDays) {
        long limiteMillis = System.currentTimeMillis()
                - (retentionDays * 24L * 60L * 60L * 1000L);

        try (var stream = Files.list(backupDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".db") || name.endsWith(".zip");
                    })
                    .sorted(Comparator.comparingLong(this::safeLastModified))
                    .forEach(path -> {
                        long lastModified = safeLastModified(path);
                        if (lastModified < limiteMillis) {
                            try {
                                Files.deleteIfExists(path);
                                log.info("Backup antiguo eliminado: {}", path);
                            } catch (IOException ex) {
                                log.warn("No se pudo eliminar backup antiguo {}: {}", path, ex.getMessage());
                            }
                        }
                    });
        } catch (IOException ex) {
            log.warn("No se pudo aplicar politica de retencion de backups: {}", ex.getMessage());
        }
    }

    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return Long.MAX_VALUE;
        }
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private void markRemoteUploadFailure(BackupRecord record, RemoteBackupException ex) {
        int currentAttempts = record.getIntentosSubida() == null ? 0 : record.getIntentosSubida();
        int nextAttempts = currentAttempts + 1;
        boolean exhausted = nextAttempts >= MAX_REMOTE_UPLOAD_ATTEMPTS;

        record.setIntentosSubida(nextAttempts);
        record.setEstado(ex.isRetryable() && !exhausted ? BackupEstado.PENDING_UPLOAD : BackupEstado.FAILED_UPLOAD);

        if (ex.isRetryable() && exhausted) {
            record.setMensaje(ex.getMessage() + " Se alcanzo el maximo de " + MAX_REMOTE_UPLOAD_ATTEMPTS + " intentos automaticos.");
            return;
        }

        if (ex.isRetryable()) {
            record.setMensaje(ex.getMessage() + " Se reintentara automaticamente.");
            return;
        }

        record.setMensaje(ex.getMessage());
    }

    private String validateAndNormalizeDirectory(String rawDirectory) {
        String normalizedDirectory = AppStoragePaths.resolveConfiguredBackupDirectory(rawDirectory);
        Path directory = Paths.get(normalizedDirectory);

        try {
            ensureBackupDirectoryWritable(directory);
        } catch (IOException ex) {
            throw new BusinessException(resolveBackupIoMessage(ex,
                    "No se pudo preparar la carpeta de backups. Revisa permisos, espacio en disco o la ruta configurada."));
        }

        return normalizedDirectory;
    }

    private void ensureBackupDirectoryWritable(Path directory) throws IOException {
        Files.createDirectories(directory);

        Path probe = Files.createTempFile(directory, "backup-write-check-", ".tmp");
        Files.deleteIfExists(probe);
    }

    private String resolveBackupIoMessage(IOException ex, String fallbackMessage) {
        if (ex instanceof AccessDeniedException) {
            return "La carpeta de backups no tiene permisos de escritura.";
        }

        if (ex instanceof FileSystemException fileSystemException) {
            String reason = fileSystemException.getReason();
            if (reason != null) {
                String lowerReason = reason.toLowerCase();
                if (lowerReason.contains("not enough space")
                        || lowerReason.contains("espacio")
                        || lowerReason.contains("disk full")) {
                    return "No hay espacio suficiente en disco para generar el backup.";
                }
            }
        }

        String message = ex.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("not enough space")
                    || lowerMessage.contains("espacio")
                    || lowerMessage.contains("disk full")) {
                return "No hay espacio suficiente en disco para generar el backup.";
            }
        }

        return fallbackMessage;
    }

    private boolean isGoogleDriveReady(BackupSettings settings) {
        return Boolean.TRUE.equals(settings.getGoogleDriveEnabled())
                && settings.getGoogleOauthClientId() != null
                && !settings.getGoogleOauthClientId().isBlank()
                && settings.getGoogleOauthClientSecret() != null
                && !settings.getGoogleOauthClientSecret().isBlank()
                && settings.getGoogleOauthRefreshToken() != null
                && !settings.getGoogleOauthRefreshToken().isBlank();
    }

    private LocalDateTime resolveNextAutomaticBackupAt(BackupSettings settings) {
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return null;
        }

        try {
            CronExpression cronExpression = CronExpression.parse(settings.getCron());
            LocalDateTime reference = settings.getLastAutomaticBackupAt() != null
                    ? settings.getLastAutomaticBackupAt()
                    : LocalDateTime.now();
            return cronExpression.next(reference);
        } catch (Exception ex) {
            return null;
        }
    }
}
