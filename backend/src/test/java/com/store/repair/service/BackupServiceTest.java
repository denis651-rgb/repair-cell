package com.store.repair.service;

import com.store.repair.config.AppStoragePaths;
import com.store.repair.config.BackupProperties;
import com.store.repair.domain.BackupRecord;
import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.BackupResponse;
import com.store.repair.dto.BackupSettingsRequest;
import com.store.repair.dto.DriveConnectionTestResponse;
import com.store.repair.dto.GoogleOAuthStartResponse;
import com.store.repair.repository.BackupRecordRepository;
import com.store.repair.repository.BackupSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupRecordRepository backupRecordRepository;
    private BackupSettingsRepository backupSettingsRepository;
    private BackupService backupService;
    private BackupSettings settings;
    private RemoteBackupStorageService remoteBackupStorageService;
    private GoogleDriveBackupStorageService googleDriveBackupStorageService;

    @BeforeEach
    void setUp() throws Exception {
        backupRecordRepository = mock(BackupRecordRepository.class);
        backupSettingsRepository = mock(BackupSettingsRepository.class);
        remoteBackupStorageService = mock(RemoteBackupStorageService.class);
        googleDriveBackupStorageService = mock(GoogleDriveBackupStorageService.class);

        Path dbFile = tempDir.resolve("repair-test.db");
        DataSource dataSource = createSqliteDataSource(dbFile);
        BackupProperties backupProperties = new BackupProperties();
        backupProperties.setDirectory(tempDir.resolve("backups").toString());
        backupProperties.setZipEnabled(true);
        backupProperties.setRetentionDays(30);

        settings = BackupSettings.builder()
                .id(1L)
                .enabled(true)
                .cron("0 0 1 * * *")
                .directory(tempDir.resolve("backups").toString())
                .zipEnabled(true)
                .retentionDays(30)
                .googleDriveEnabled(false)
                .build();

        when(backupSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        backupService = new BackupService(
                dataSource,
                backupProperties,
                new BackupArtifactVerifier(),
                remoteBackupStorageService,
                googleDriveBackupStorageService,
                backupRecordRepository,
                backupSettingsRepository
        );
        ReflectionTestUtils.setField(backupService, "dbUrl", "jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    @Test
    void performManualBackup_createsZipArtifactAndPersistsRecord() throws IOException {
        BackupResponse response = backupService.performManualBackup();

        assertTrue(response.isOk());
        assertEquals("LOCAL_OK", response.getEstado());
        assertTrue(response.getArchivo().endsWith(".zip"));

        Path artifact = Path.of(response.getRutaLocal());
        assertTrue(Files.exists(artifact));
        assertTrue(Files.size(artifact) > 0);

        ArgumentCaptor<BackupRecord> captor = ArgumentCaptor.forClass(BackupRecord.class);
        verify(backupRecordRepository).save(captor.capture());
        BackupRecord saved = captor.getValue();
        assertEquals(response.getRutaLocal(), saved.getRutaLocal());
        assertEquals(response.getArchivo(), saved.getArchivo());
        assertTrue(saved.getTamanoBytes() > 0);
    }

    @Test
    void performManualBackup_withoutZipCreatesDbArtifact() {
        settings.setZipEnabled(false);

        BackupResponse response = backupService.performManualBackup();

        assertTrue(response.isOk());
        assertTrue(response.getArchivo().endsWith(".db"));
        assertTrue(Files.exists(Path.of(response.getRutaLocal())));
    }

    @Test
    void performManualBackup_appliesRetentionPolicyToOldArtifacts() throws IOException {
        settings.setRetentionDays(1);
        Path backupDir = Path.of(settings.getDirectory());
        Files.createDirectories(backupDir);

        Path oldZip = backupDir.resolve("old-backup.zip");
        Files.writeString(oldZip, "old");
        Files.setLastModifiedTime(oldZip, FileTime.from(Instant.now().minusSeconds(3 * 24 * 60 * 60L)));

        backupService.performManualBackup();

        assertFalse(Files.exists(oldZip));
    }

    @Test
    void scheduleBackup_generatesAutomaticBackupWhenCronIsDue() {
        settings.setCron("0 * * * * *");
        settings.setLastAutomaticBackupAt(LocalDateTime.now().minusMinutes(2));

        backupService.scheduleBackup();

        verify(backupRecordRepository, atLeastOnce()).save(any(BackupRecord.class));
        assertNotNull(settings.getLastAutomaticBackupAt());
    }

    @Test
    void updateSettings_resolvesRelativeDirectoryToStableAbsolutePath() {
        BackupSettingsRequest request = new BackupSettingsRequest();
        request.setEnabled(true);
        request.setCron("0 0 1 * * *");
        request.setDirectory("./backups");
        request.setZipEnabled(true);
        request.setRetentionDays(10);
        request.setGoogleDriveEnabled(false);

        backupService.updateSettings(request);

        assertEquals(AppStoragePaths.resolveBackupDirectory(), settings.getDirectory());
        verify(backupSettingsRepository, atLeastOnce()).save(settings);
    }

    @Test
    void performManualBackup_withDriveUploadSuccess_marksRemoteOk() {
        settings.setGoogleDriveEnabled(true);
        settings.setGoogleOauthClientId("demo-client-id");
        settings.setGoogleOauthRefreshToken("refresh-token");
        when(remoteBackupStorageService.upload(any(Path.class), any(BackupSettings.class)))
                .thenReturn("https://drive.google.com/file/d/demo/view");

        BackupResponse response = backupService.performManualBackup();

        assertEquals("REMOTE_OK", response.getEstado());
        ArgumentCaptor<BackupRecord> captor = ArgumentCaptor.forClass(BackupRecord.class);
        verify(backupRecordRepository, atLeastOnce()).save(captor.capture());
        assertEquals("REMOTE_OK", captor.getValue().getEstado().name());
    }

    @Test
    void performManualBackup_withoutConnection_marksPendingUpload() {
        settings.setGoogleDriveEnabled(true);
        settings.setGoogleOauthClientId("demo-client-id");
        settings.setGoogleOauthRefreshToken("refresh-token");
        when(remoteBackupStorageService.upload(any(Path.class), any(BackupSettings.class)))
                .thenThrow(new RemoteBackupException("No hay conexion disponible para comunicarse con Google Drive.", true));

        BackupResponse response = backupService.performManualBackup();

        assertEquals("PENDING_UPLOAD", response.getEstado());
        assertTrue(response.getMensaje().contains("reintentara"));
    }

    @Test
    void retryPendingUploads_whenConnectionReturns_updatesRecordToRemoteOk() {
        settings.setGoogleDriveEnabled(true);
        settings.setGoogleOauthClientId("demo-client-id");
        settings.setGoogleOauthRefreshToken("refresh-token");
        BackupRecord pending = BackupRecord.builder()
                .archivo("pending.zip")
                .rutaLocal(createFakeBackupFile().toString())
                .estado(com.store.repair.domain.BackupEstado.PENDING_UPLOAD)
                .origen(com.store.repair.domain.BackupOrigen.MANUAL)
                .generadoEn(LocalDateTime.now().minusMinutes(5))
                .intentosSubida(1)
                .build();
        when(backupRecordRepository.findTop20ByEstadoOrderByGeneradoEnAsc(com.store.repair.domain.BackupEstado.PENDING_UPLOAD))
                .thenReturn(List.of(pending));
        when(remoteBackupStorageService.upload(any(Path.class), any(BackupSettings.class)))
                .thenReturn("https://drive.google.com/file/d/final/view");

        int retried = backupService.retryPendingUploads();

        assertEquals(1, retried);
        assertEquals(com.store.repair.domain.BackupEstado.REMOTE_OK, pending.getEstado());
    }

    @Test
    void testDriveConnection_returnsDriveValidationResult() {
        when(remoteBackupStorageService.testConnection(any(BackupSettings.class)))
                .thenReturn(DriveConnectionTestResponse.builder()
                        .ok(true)
                        .retryable(false)
                        .message("Conexion con Google Drive verificada correctamente.")
                        .folderId("drive-folder-id")
                        .folderName("TallerCelularBackups")
                        .checkedAt(LocalDateTime.now().toString())
                        .build());

        DriveConnectionTestResponse response = backupService.testDriveConnection();

        assertTrue(response.isOk());
        assertEquals("TallerCelularBackups", response.getFolderName());
    }

    @Test
    void startGoogleDriveOAuth_persistsClientIdAndDelegatesToOAuthService() {
        when(googleDriveBackupStorageService.startAuthorization(any(BackupSettings.class)))
                .thenReturn(GoogleOAuthStartResponse.builder()
                        .authUrl("https://accounts.google.com/o/oauth2/v2/auth?demo")
                        .state("demo-state")
                        .build());

        BackupSettingsRequest request = new BackupSettingsRequest();
        request.setEnabled(true);
        request.setCron("0 0 1 * * *");
        request.setDirectory(tempDir.resolve("backups").toString());
        request.setZipEnabled(true);
        request.setRetentionDays(10);
        request.setGoogleDriveEnabled(true);
        request.setGoogleOauthClientId("desktop-client-id.apps.googleusercontent.com");
        request.setGoogleOauthClientSecret("demo-client-secret");

        GoogleOAuthStartResponse response = backupService.startGoogleDriveOAuth(request);

        assertEquals("demo-state", response.getState());
        assertEquals("desktop-client-id.apps.googleusercontent.com", settings.getGoogleOauthClientId());
        assertEquals("demo-client-secret", settings.getGoogleOauthClientSecret());
    }

    private DataSource createSqliteDataSource(Path dbFile) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS smoke_test (id INTEGER PRIMARY KEY, nombre TEXT)");
            statement.execute("INSERT INTO smoke_test (nombre) VALUES ('ok')");
        }

        return dataSource;
    }

    private Path createFakeBackupFile() {
        try {
            Path file = tempDir.resolve("pending-upload.zip");
            Files.writeString(file, "demo");
            return file;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
