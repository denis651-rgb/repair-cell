package com.store.repair.service;

import com.store.repair.dto.RestoreLocalValidationResponse;
import com.store.repair.repository.AuditoriaRepository;
import com.store.repair.repository.BackupSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupRestoreServiceTest {

    @TempDir
    Path tempDir;

    private String previousAppStorageDir;

    @BeforeEach
    void setUp() {
        previousAppStorageDir = System.getProperty("APP_STORAGE_DIR");
        System.setProperty("APP_STORAGE_DIR", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousAppStorageDir == null) {
            System.clearProperty("APP_STORAGE_DIR");
        } else {
            System.setProperty("APP_STORAGE_DIR", previousAppStorageDir);
        }
    }

    @Test
    void validateLocalBackup_acceptsValidDbFile() throws Exception {
        BackupRestoreService service = createService();
        Path sqliteFile = createSqliteBackup(tempDir.resolve("valid-backup.db"));
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "valid-backup.db",
                "application/octet-stream",
                Files.readAllBytes(sqliteFile)
        );

        RestoreLocalValidationResponse response = service.validateLocalBackup(multipartFile);

        assertEquals("DB", response.getFormat());
        assertEquals("valid-backup.db", response.getOriginalFileName());
        assertTrue(response.getSessionId() != null && !response.getSessionId().isBlank());
    }

    @Test
    void validateLocalBackup_rejectsZipWithMultipleDatabases() throws Exception {
        BackupRestoreService service = createService();
        Path firstDb = createSqliteBackup(tempDir.resolve("one.db"));
        Path secondDb = createSqliteBackup(tempDir.resolve("two.db"));
        byte[] zipBytes = createZip(firstDb, secondDb);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "invalid.zip",
                "application/zip",
                zipBytes
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.validateLocalBackup(multipartFile));

        assertTrue(exception.getMessage().contains("exactamente una base"));
    }

    @Test
    void validateRemoteBackup_reusesSameValidationPipeline() throws Exception {
        Path sqliteFile = createSqliteBackup(tempDir.resolve("remote-valid.db"));
        RemoteBackupStorageService remoteBackupStorageService = mock(RemoteBackupStorageService.class);
        BackupRestoreService service = createService(remoteBackupStorageService);

        when(remoteBackupStorageService.downloadBackup(
                org.mockito.ArgumentMatchers.eq("drive-file-1"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Path targetDirectory = invocation.getArgument(2);
                    Path targetFile = targetDirectory.resolve("drive-backup.db");
                    Files.copy(sqliteFile, targetFile);
                    return new RemoteBackupStorageService.DownloadedBackup(
                            "drive-file-1",
                            "drive-backup.db",
                            Files.size(targetFile),
                            "2026-04-25T12:00:00",
                            targetFile
                    );
                });

        RestoreLocalValidationResponse response = service.validateRemoteBackup("drive-file-1");

        assertEquals("DB", response.getFormat());
        assertEquals("drive-backup.db", response.getOriginalFileName());
        assertTrue(response.getSessionId() != null && !response.getSessionId().isBlank());
    }

    private BackupRestoreService createService() {
        return createService(mock(RemoteBackupStorageService.class));
    }

    private BackupRestoreService createService(RemoteBackupStorageService remoteBackupStorageService) {
        DataSource dataSource = new DriverManagerDataSource();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        AuditoriaRepository auditoriaRepository = mock(AuditoriaRepository.class);
        BackupSettingsRepository backupSettingsRepository = mock(BackupSettingsRepository.class);
        when(backupSettingsRepository.findAll()).thenReturn(List.of(
                com.store.repair.domain.BackupSettings.builder()
                        .id(1L)
                        .googleDriveEnabled(true)
                        .googleOauthClientId("client-id")
                        .googleOauthClientSecret("client-secret")
                        .googleOauthRefreshToken("refresh-token")
                        .build()
        ));

        BackupRestoreService service = new BackupRestoreService(
                dataSource,
                auditoriaRepository,
                applicationContext,
                remoteBackupStorageService,
                backupSettingsRepository
        );
        ReflectionTestUtils.setField(service, "dbUrl", "jdbc:sqlite:" + tempDir.resolve("active.db").toAbsolutePath());
        service.initializeRestorePaths();
        return service;
    }

    private Path createSqliteBackup(Path file) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS usuarios (id INTEGER PRIMARY KEY, nombre TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS clientes (id INTEGER PRIMARY KEY, nombre TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS backup_settings (id INTEGER PRIMARY KEY, cron TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS backup_records (id INTEGER PRIMARY KEY, archivo TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS ordenes_reparacion (id INTEGER PRIMARY KEY, numero_orden TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS productos_inventario (id INTEGER PRIMARY KEY, nombre TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS ventas (id INTEGER PRIMARY KEY, numero_comprobante TEXT)");
            statement.execute("INSERT INTO usuarios (nombre) VALUES ('admin')");
        }

        return file;
    }

    private byte[] createZip(Path... sqliteFiles) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Path sqliteFile : sqliteFiles) {
                zipOutputStream.putNextEntry(new ZipEntry(sqliteFile.getFileName().toString()));
                zipOutputStream.write(Files.readAllBytes(sqliteFile));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
