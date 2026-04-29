package com.store.repair.service;

import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.GoogleOAuthStartResponse;
import com.store.repair.repository.BackupSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GoogleDriveBackupStorageServiceTest {

    @Test
    void startAuthorization_buildsGoogleAuthUrlWithPkceAndLoopbackCallback() {
        GoogleDriveBackupStorageService service = new GoogleDriveBackupStorageService(mock(BackupSettingsRepository.class));
        ReflectionTestUtils.setField(service, "serverPort", 8080);

        BackupSettings settings = BackupSettings.builder()
                .id(1L)
                .googleOauthClientId("desktop-client-id.apps.googleusercontent.com")
                .googleOauthClientSecret("demo-client-secret")
                .build();

        GoogleOAuthStartResponse response = service.startAuthorization(settings);

        assertTrue(response.getAuthUrl().contains("code_challenge="));
        assertTrue(response.getAuthUrl().contains("code_challenge_method=S256"));
        assertTrue(response.getAuthUrl().contains("client_id=desktop-client-id.apps.googleusercontent.com"));
        assertTrue(response.getAuthUrl().contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A8080%2Fapi%2Fadmin%2Fbackups%2Foauth%2Fcallback"));
        assertEquals(true, response.getState() != null && !response.getState().isBlank());
    }

    @Test
    void googleApiMessageExplainsRevokedPermissionsAndQuota() {
        GoogleDriveBackupStorageService service = new GoogleDriveBackupStorageService(mock(BackupSettingsRepository.class));

        String revokedMessage = ReflectionTestUtils.invokeMethod(
                service,
                "buildGoogleApiMessage",
                "Google OAuth rechazo la renovacion del token.",
                "{\"error\":\"invalid_grant\"}");

        String quotaMessage = ReflectionTestUtils.invokeMethod(
                service,
                "buildGoogleApiMessage",
                "Google Drive rechazo la subida.",
                "{\"error\":{\"errors\":[{\"reason\":\"storageQuotaExceeded\"}]}}");

        assertTrue(revokedMessage.contains("revoco o vencio"));
        assertTrue(quotaMessage.contains("No hay espacio suficiente en Google Drive"));
    }
}
