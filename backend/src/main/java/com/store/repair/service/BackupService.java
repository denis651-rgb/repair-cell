package com.store.repair.service;

import com.store.repair.config.BackupProperties;
import com.store.repair.domain.BackupEstado;
import com.store.repair.domain.BackupOrigen;
import com.store.repair.domain.BackupRecord;
import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.BackupResponse;
import com.store.repair.dto.BackupSettingsRequest;
import com.store.repair.dto.BackupSettingsResponse;
import com.store.repair.dto.BackupSummaryResponse;
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

    private final DataSource dataSource;
    private final BackupProperties backupProperties;
    private final RemoteBackupStorageService remoteBackupStorageService;
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
                        .directory(backupProperties.getDirectory())
                        .zipEnabled(backupProperties.isZipEnabled())
                        .retentionDays(backupProperties.getRetentionDays())
                        .googleDriveEnabled(backupProperties.isGoogleDriveEnabled())
                        .googleDriveFolderId(safeTrim(backupProperties.getGoogleDriveFolderId()))
                        .googleServiceAccountKeyPath(safeTrim(backupProperties.getGoogleServiceAccountKeyPath()))
                        .build()));
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
                .googleServiceAccountKeyPath(settings.getGoogleServiceAccountKeyPath())
                .googleDriveReady(isGoogleDriveReady(settings))
                .lastAutomaticBackupAt(settings.getLastAutomaticBackupAt() == null ? null : settings.getLastAutomaticBackupAt().toString())
                .nextAutomaticBackupAt(nextAutomaticBackupAt == null ? null : nextAutomaticBackupAt.toString())
                .build();
    }

    public BackupSettingsResponse updateSettings(BackupSettingsRequest request) {
        validateRequest(request);

        BackupSettings settings = getSettingsEntity();
        settings.setEnabled(request.isEnabled());
        settings.setCron(request.getCron().trim());
        settings.setDirectory(request.getDirectory().trim());
        settings.setZipEnabled(request.isZipEnabled());
        settings.setRetentionDays(request.getRetentionDays());
        settings.setGoogleDriveEnabled(request.isGoogleDriveEnabled());
        settings.setGoogleDriveFolderId(safeTrim(request.getGoogleDriveFolderId()));
        settings.setGoogleServiceAccountKeyPath(safeTrim(request.getGoogleServiceAccountKeyPath()));
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
                record.setEstado(ex.isRetryable() ? BackupEstado.PENDING_UPLOAD : BackupEstado.FAILED_UPLOAD);
                record.setMensaje(ex.getMessage());
                record.setUltimoIntentoSubidaEn(LocalDateTime.now());
                record.setIntentosSubida(record.getIntentosSubida() + 1);
                backupRecordRepository.save(record);
            }
        }

        return retried;
    }

    private BackupResponse performBackupInterno(BackupOrigen origen) {
        if (!backupLock.tryLock()) {
            throw new IllegalStateException("Ya hay un backup en ejecucion");
        }

        try {
            BackupSettings settings = getSettingsEntity();
            Path backupDir = Paths.get(settings.getDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(FORMATTER);
            String baseName = "repair-backup-" + timestamp;
            Path tempBackupDb = backupDir.resolve(baseName + ".db");

            ejecutarVacuumInto(tempBackupDb);

            Path finalArtifact = tempBackupDb;
            if (Boolean.TRUE.equals(settings.getZipEnabled())) {
                finalArtifact = comprimirZip(tempBackupDb);
                Files.deleteIfExists(tempBackupDb);
            }

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
                    record.setEstado(ex.isRetryable() ? BackupEstado.PENDING_UPLOAD : BackupEstado.FAILED_UPLOAD);
                    record.setMensaje(ex.getMessage());
                    record.setUltimoIntentoSubidaEn(LocalDateTime.now());
                    record.setIntentosSubida(1);
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
            throw new IllegalStateException("No se pudo crear el backup: " + ex.getMessage(), ex);
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
            throw new IllegalStateException("La expresion cron es obligatoria");
        }

        try {
            CronExpression.parse(request.getCron().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("La expresion cron no es valida");
        }

        if (request.getDirectory() == null || request.getDirectory().isBlank()) {
            throw new IllegalStateException("La carpeta local es obligatoria");
        }

        Path directory = Paths.get(request.getDirectory().trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo preparar la carpeta de backups: " + ex.getMessage(), ex);
        }

        if (request.getRetentionDays() < 1) {
            throw new IllegalStateException("La retencion minima es de 1 dia");
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
            throw new IllegalStateException("No se pudo generar backup SQLite con VACUUM INTO: " + ex.getMessage(), ex);
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
            throw new IllegalStateException("No se pudo comprimir el backup: " + ex.getMessage(), ex);
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

    private boolean isGoogleDriveReady(BackupSettings settings) {
        return Boolean.TRUE.equals(settings.getGoogleDriveEnabled())
                && settings.getGoogleDriveFolderId() != null
                && !settings.getGoogleDriveFolderId().isBlank()
                && settings.getGoogleServiceAccountKeyPath() != null
                && !settings.getGoogleServiceAccountKeyPath().isBlank();
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
