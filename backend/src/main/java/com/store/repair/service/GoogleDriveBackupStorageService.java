package com.store.repair.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.repair.domain.BackupSettings;
import com.store.repair.dto.DriveConnectionTestResponse;
import com.store.repair.dto.GoogleOAuthStartResponse;
import com.store.repair.repository.BackupSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GoogleDriveBackupStorageService implements RemoteBackupStorageService {

    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String APP_FOLDER_NAME = "TallerCelularBackups";
    private static final long OAUTH_STATE_TTL_MINUTES = 15;

    private final BackupSettingsRepository backupSettingsRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingOauthSession> pendingOauthSessions = new ConcurrentHashMap<>();

    @Value("${server.port:8080}")
    private int serverPort;

    @Override
    public String upload(Path file, BackupSettings settings) {
        try {
            validateConnectedSettings(settings);
            String accessToken = requestAccessTokenFromRefreshToken(settings);
            DriveFolder folder = ensureAppFolder(settings, accessToken);
            return uploadFile(file, accessToken, folder.id());
        } catch (RemoteBackupException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RemoteBackupException("La subida a Google Drive fue interrumpida.", ex, true);
        } catch (Exception ex) {
            throw buildUnexpectedRemoteException("No se pudo subir el backup a Google Drive.", ex);
        }
    }

    @Override
    public DriveConnectionTestResponse testConnection(BackupSettings settings) {
        try {
            validateConnectedSettings(settings);
            String accessToken = requestAccessTokenFromRefreshToken(settings);
            DriveFolder folder = ensureAppFolder(settings, accessToken);

            return DriveConnectionTestResponse.builder()
                    .ok(true)
                    .retryable(false)
                    .message("Conexion con Google Drive verificada correctamente.")
                    .folderId(folder.id())
                    .folderName(folder.name())
                    .checkedAt(LocalDateTime.now().toString())
                    .build();
        } catch (RemoteBackupException ex) {
            return buildFailedResponse(settings, ex.getMessage(), ex.isRetryable());
        } catch (Exception ex) {
            RemoteBackupException remoteException = buildUnexpectedRemoteException(
                    "No se pudo validar la conexion con Google Drive.", ex);
            return buildFailedResponse(settings, remoteException.getMessage(), remoteException.isRetryable());
        }
    }

    @Override
    public List<RemoteBackupFileDescriptor> listAvailableBackups(BackupSettings settings) {
        try {
            validateConnectedSettings(settings);
            String accessToken = requestAccessTokenFromRefreshToken(settings);
            DriveFolder folder = ensureAppFolder(settings, accessToken);
            return listFiles(accessToken, folder.id());
        } catch (RemoteBackupException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RemoteBackupException("La consulta de backups remotos fue interrumpida.", ex, true);
        } catch (Exception ex) {
            throw buildUnexpectedRemoteException("No se pudieron listar los backups de Google Drive.", ex);
        }
    }

    @Override
    public DownloadedBackup downloadBackup(String fileId, BackupSettings settings, Path targetDirectory) {
        if (fileId == null || fileId.isBlank()) {
            throw new RemoteBackupException("Debes indicar el archivo remoto de Google Drive para restaurar.", false);
        }

        try {
            validateConnectedSettings(settings);
            String accessToken = requestAccessTokenFromRefreshToken(settings);
            DriveFolder folder = ensureAppFolder(settings, accessToken);
            JsonNode fileMetadata = fetchFileMetadata(fileId, accessToken);
            verifyFileBelongsToFolder(fileMetadata, folder.id());

            String fileName = sanitizeFileName(fileMetadata.path("name").asText("drive-backup.db"));
            if (!isSupportedBackupFile(fileName)) {
                throw new RemoteBackupException("El archivo remoto no tiene extension .db o .zip y no puede restaurarse.", false);
            }

            java.nio.file.Files.createDirectories(targetDirectory);
            Path targetFile = targetDirectory.resolve(fileName);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/drive/v3/files/" + fileId
                            + "?alt=media&supportsAllDrives=true"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetFile));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                java.nio.file.Files.deleteIfExists(targetFile);
                boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
                throw new RemoteBackupException(
                        buildGoogleApiMessage("Google Drive no pudo descargar el backup remoto.", readResponseBody(targetFile)),
                        retryable
                );
            }

            long sizeBytes = fileMetadata.path("size").asLong(java.nio.file.Files.size(targetFile));
            return new DownloadedBackup(
                    fileId,
                    fileName,
                    sizeBytes,
                    fileMetadata.path("createdTime").asText(null),
                    targetFile
            );
        } catch (RemoteBackupException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RemoteBackupException("La descarga del backup remoto fue interrumpida.", ex, true);
        } catch (Exception ex) {
            throw buildUnexpectedRemoteException("No se pudo descargar el backup remoto desde Google Drive.", ex);
        }
    }

    public GoogleOAuthStartResponse startAuthorization(BackupSettings settings) {
        if (settings == null || settings.getId() == null) {
            throw new BusinessException("No se encontro la configuracion de backups para iniciar Google OAuth.");
        }

        if (settings.getGoogleOauthClientId() == null || settings.getGoogleOauthClientId().isBlank()) {
            throw new BusinessException("Debes indicar el Client ID de Google OAuth antes de conectar Drive.");
        }

        if (settings.getGoogleOauthClientSecret() == null || settings.getGoogleOauthClientSecret().isBlank()) {
            throw new BusinessException("Debes indicar el Client Secret de Google OAuth antes de conectar Drive.");
        }

        cleanupExpiredSessions();

        String state = randomUrlSafeToken(24);
        String codeVerifier = randomUrlSafeToken(64);
        String codeChallenge = sha256Base64Url(codeVerifier);
        pendingOauthSessions.put(state, new PendingOauthSession(settings.getId(), codeVerifier, System.currentTimeMillis()));

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + urlEncode(settings.getGoogleOauthClientId())
                + "&redirect_uri=" + urlEncode(getRedirectUri())
                + "&response_type=code"
                + "&scope=" + urlEncode(DRIVE_SCOPE)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + urlEncode(state)
                + "&code_challenge=" + urlEncode(codeChallenge)
                + "&code_challenge_method=S256";

        return GoogleOAuthStartResponse.builder()
                .authUrl(authUrl)
                .state(state)
                .build();
    }

    public String completeAuthorization(String state, String code, String error, String errorDescription) {
        if (error != null && !error.isBlank()) {
            String details = errorDescription == null || errorDescription.isBlank() ? error : errorDescription;
            return buildCallbackHtml(false, "Google rechazo la autorizacion: " + details);
        }

        if (state == null || state.isBlank() || code == null || code.isBlank()) {
            return buildCallbackHtml(false, "La respuesta de Google OAuth llego incompleta.");
        }

        PendingOauthSession session = pendingOauthSessions.remove(state);
        if (session == null || session.isExpired()) {
            return buildCallbackHtml(false, "La sesion de autorizacion expiro. Vuelve a pulsar \"Conectar Google Drive\".");
        }

        BackupSettings settings = backupSettingsRepository.findById(session.settingsId())
                .orElse(null);
        if (settings == null) {
            return buildCallbackHtml(false, "No se encontro la configuracion de backups para completar Google OAuth.");
        }

        try {
            TokenResponse tokenResponse = exchangeAuthorizationCode(settings.getGoogleOauthClientId(), code, session.codeVerifier());
            String refreshToken = tokenResponse.refreshToken();
            if ((refreshToken == null || refreshToken.isBlank())
                    && (settings.getGoogleOauthRefreshToken() == null || settings.getGoogleOauthRefreshToken().isBlank())) {
                return buildCallbackHtml(false, "Google no devolvio refresh_token. Vuelve a conectar con consentimiento forzado.");
            }

            DriveFolder folder = ensureAppFolder(settings, tokenResponse.accessToken());
            if (refreshToken != null && !refreshToken.isBlank()) {
                settings.setGoogleOauthRefreshToken(refreshToken);
            }
            settings.setGoogleDriveFolderId(folder.id());
            settings.setGoogleDriveFolderName(folder.name());
            settings.setGoogleOauthConnectedAt(LocalDateTime.now());
            settings.setGoogleDriveEnabled(true);
            backupSettingsRepository.save(settings);

            return buildCallbackHtml(true, "Google Drive conectado correctamente. Ya puedes volver a la app.");
        } catch (RemoteBackupException ex) {
            return buildCallbackHtml(false, ex.getMessage());
        } catch (Exception ex) {
            RemoteBackupException remoteException = buildUnexpectedRemoteException(
                    "No se pudo completar la conexion con Google Drive.", ex);
            return buildCallbackHtml(false, remoteException.getMessage());
        }
    }

    public void disconnect(BackupSettings settings) {
        settings.setGoogleOauthRefreshToken(null);
        settings.setGoogleOauthConnectedAt(null);
        settings.setGoogleDriveFolderId(null);
        settings.setGoogleDriveFolderName(null);
        backupSettingsRepository.save(settings);
    }

    private DriveConnectionTestResponse buildFailedResponse(BackupSettings settings, String message, boolean retryable) {
        return DriveConnectionTestResponse.builder()
                .ok(false)
                .retryable(retryable)
                .message(message)
                .folderId(settings == null ? null : settings.getGoogleDriveFolderId())
                .folderName(settings == null ? null : settings.getGoogleDriveFolderName())
                .checkedAt(LocalDateTime.now().toString())
                .build();
    }

    private void validateConnectedSettings(BackupSettings settings) {
        if (settings == null) {
            throw new RemoteBackupException("No se recibio configuracion para Google Drive.", false);
        }

        if (!Boolean.TRUE.equals(settings.getGoogleDriveEnabled())) {
            throw new RemoteBackupException("Google Drive no esta habilitado en la configuracion.", false);
        }

        if (settings.getGoogleOauthClientId() == null || settings.getGoogleOauthClientId().isBlank()) {
            throw new RemoteBackupException("Falta el Client ID de Google OAuth.", false);
        }

        if (settings.getGoogleOauthClientSecret() == null || settings.getGoogleOauthClientSecret().isBlank()) {
            throw new RemoteBackupException("Falta el Client Secret de Google OAuth.", false);
        }

        if (settings.getGoogleOauthRefreshToken() == null || settings.getGoogleOauthRefreshToken().isBlank()) {
            throw new RemoteBackupException("Google Drive todavia no esta conectado. Pulsa \"Conectar Google Drive\".", false);
        }
    }

    private TokenResponse exchangeAuthorizationCode(String clientId, String code, String codeVerifier)
            throws IOException, InterruptedException {
        String body = "grant_type=" + urlEncode("authorization_code")
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(resolveRequiredClientSecret(clientId))
                + "&code=" + urlEncode(code)
                + "&redirect_uri=" + urlEncode(getRedirectUri())
                + "&code_verifier=" + urlEncode(codeVerifier);

        JsonNode tokenResponse = sendTokenRequest(body, "Google OAuth rechazo el intercambio del codigo de autorizacion.");
        String accessToken = tokenResponse.path("access_token").asText();
        if (accessToken.isBlank()) {
            throw new RemoteBackupException("Google OAuth no devolvio access_token.", false);
        }

        return new TokenResponse(accessToken, tokenResponse.path("refresh_token").asText(null));
    }

    private String requestAccessTokenFromRefreshToken(BackupSettings settings) throws IOException, InterruptedException {
        String body = "grant_type=" + urlEncode("refresh_token")
                + "&client_id=" + urlEncode(settings.getGoogleOauthClientId())
                + "&client_secret=" + urlEncode(settings.getGoogleOauthClientSecret())
                + "&refresh_token=" + urlEncode(settings.getGoogleOauthRefreshToken());

        JsonNode tokenResponse = sendTokenRequest(body, "Google OAuth rechazo la renovacion del token.");
        String accessToken = tokenResponse.path("access_token").asText();
        if (accessToken.isBlank()) {
            throw new RemoteBackupException("Google OAuth no devolvio access_token.", false);
        }
        return accessToken;
    }

    private JsonNode sendTokenRequest(String body, String prefix) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException(buildGoogleApiMessage(prefix, response.body()), retryable);
        }
        return objectMapper.readTree(response.body());
    }

    private DriveFolder ensureAppFolder(BackupSettings settings, String accessToken) throws IOException, InterruptedException {
        String existingFolderId = settings.getGoogleDriveFolderId();
        if (existingFolderId != null && !existingFolderId.isBlank()) {
            try {
                JsonNode folderNode = fetchFolder(existingFolderId, accessToken);
                DriveFolder folder = new DriveFolder(
                        folderNode.path("id").asText(existingFolderId),
                        folderNode.path("name").asText(APP_FOLDER_NAME)
                );
                persistFolderIfChanged(settings, folder);
                return folder;
            } catch (RemoteBackupException ex) {
                if (ex.isRetryable()) {
                    throw ex;
                }
            }
        }

        DriveFolder folder = createFolder(accessToken);
        persistFolderIfChanged(settings, folder);
        return folder;
    }

    private List<RemoteBackupFileDescriptor> listFiles(String accessToken, String folderId) throws IOException, InterruptedException {
        String query = "'" + folderId + "' in parents and trashed=false";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri(
                        "https://www.googleapis.com/drive/v3/files",
                        "supportsAllDrives", "true",
                        "includeItemsFromAllDrives", "true",
                        "fields", "files(id,name,size,createdTime,modifiedTime,mimeType)",
                        "orderBy", "createdTime desc",
                        "pageSize", "100",
                        "q", query
                ))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException(
                    buildGoogleApiMessage("Google Drive no pudo listar los backups remotos.", response.body()),
                    retryable
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        List<RemoteBackupFileDescriptor> files = new ArrayList<>();
        for (JsonNode fileNode : root.path("files")) {
            String name = fileNode.path("name").asText("");
            if (!isSupportedBackupFile(name)) {
                continue;
            }
            files.add(new RemoteBackupFileDescriptor(
                    fileNode.path("id").asText(),
                    name,
                    fileNode.path("size").asLong(0L),
                    fileNode.path("createdTime").asText(null),
                    fileNode.path("modifiedTime").asText(null)
            ));
        }

        files.sort(Comparator.comparing(RemoteBackupFileDescriptor::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return files;
    }

    private void persistFolderIfChanged(BackupSettings settings, DriveFolder folder) {
        boolean changed = !folder.id().equals(settings.getGoogleDriveFolderId())
                || !folder.name().equals(settings.getGoogleDriveFolderName());
        if (!changed) {
            return;
        }

        settings.setGoogleDriveFolderId(folder.id());
        settings.setGoogleDriveFolderName(folder.name());
        backupSettingsRepository.save(settings);
    }

    private JsonNode fetchFolder(String folderId, String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/drive/v3/files/" + folderId
                        + "?fields=id,name,mimeType&supportsAllDrives=true"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException(
                    buildGoogleApiMessage("Google Drive rechazo la validacion de la carpeta.", response.body()),
                    retryable);
        }

        JsonNode folderNode = objectMapper.readTree(response.body());
        String mimeType = folderNode.path("mimeType").asText();
        if (!"application/vnd.google-apps.folder".equals(mimeType)) {
            throw new RemoteBackupException("El recurso configurado en Google Drive no es una carpeta.", false);
        }
        return folderNode;
    }

    private JsonNode fetchFileMetadata(String fileId, String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/drive/v3/files/" + fileId
                        + "?fields=id,name,size,createdTime,modifiedTime,mimeType,parents&supportsAllDrives=true"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException(
                    buildGoogleApiMessage("Google Drive no pudo leer el archivo remoto seleccionado.", response.body()),
                    retryable
            );
        }

        return objectMapper.readTree(response.body());
    }

    private void verifyFileBelongsToFolder(JsonNode fileNode, String folderId) {
        JsonNode parents = fileNode.path("parents");
        for (JsonNode parent : parents) {
            if (folderId.equals(parent.asText())) {
                return;
            }
        }
        throw new RemoteBackupException("El archivo remoto seleccionado no pertenece a la carpeta de backups de esta app.", false);
    }

    private DriveFolder createFolder(String accessToken) throws IOException, InterruptedException {
        String metadataJson = objectMapper.writeValueAsString(Map.of(
                "name", APP_FOLDER_NAME,
                "mimeType", "application/vnd.google-apps.folder"
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id,name,mimeType"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(metadataJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException(buildGoogleApiMessage("Google Drive no pudo crear la carpeta de backups.", response.body()), retryable);
        }

        JsonNode folderNode = objectMapper.readTree(response.body());
        return new DriveFolder(
                folderNode.path("id").asText(),
                folderNode.path("name").asText(APP_FOLDER_NAME)
        );
    }

    private String uploadFile(Path file, String accessToken, String folderId) throws IOException, InterruptedException {
        String boundary = "backup-" + UUID.randomUUID();
        String metadataJson = objectMapper.writeValueAsString(Map.of(
                "name", file.getFileName().toString(),
                "parents", new String[]{ folderId }
        ));

        byte[] fileBytes = java.nio.file.Files.readAllBytes(file);
        byte[] body = buildMultipartBody(boundary, metadataJson, fileBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException(buildGoogleApiMessage("Google Drive rechazo la subida.", response.body()), retryable);
        }

        JsonNode driveResponse = objectMapper.readTree(response.body());
        String fileId = driveResponse.path("id").asText();
        if (fileId.isBlank()) {
            throw new RemoteBackupException("Google Drive no devolvio el ID del archivo subido.", true);
        }

        return "https://drive.google.com/file/d/" + fileId + "/view";
    }

    private boolean isSupportedBackupFile(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        return lower.endsWith(".db") || lower.endsWith(".zip");
    }

    private URI buildUri(String baseUrl, String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Los parametros de query deben venir en pares clave/valor.");
        }

        StringBuilder builder = new StringBuilder(baseUrl);
        if (keyValues.length > 0) {
            builder.append('?');
        }

        for (int index = 0; index < keyValues.length; index += 2) {
            if (index > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(keyValues[index]))
                    .append('=')
                    .append(urlEncode(keyValues[index + 1]));
        }

        try {
            return URI.create(builder.toString());
        } catch (IllegalArgumentException ex) {
            throw new RemoteBackupException(
                    "No se pudo preparar la consulta a Google Drive. Vuelve a intentarlo en unos segundos.",
                    ex,
                    true
            );
        }
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "drive-backup.db";
        }

        String normalized = value.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String readResponseBody(Path targetFile) {
        try {
            if (java.nio.file.Files.exists(targetFile)) {
                return java.nio.file.Files.readString(targetFile);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private byte[] buildMultipartBody(String boundary, String metadataJson, byte[] fileBytes) {
        byte[] firstPart = (
                "--" + boundary + "\r\n"
                        + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                        + metadataJson + "\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] endPart = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[firstPart.length + fileBytes.length + endPart.length];
        System.arraycopy(firstPart, 0, body, 0, firstPart.length);
        System.arraycopy(fileBytes, 0, body, firstPart.length, fileBytes.length);
        System.arraycopy(endPart, 0, body, firstPart.length + fileBytes.length, endPart.length);
        return body;
    }

    private String buildGoogleApiMessage(String prefix, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String error = root.path("error").asText();
            String errorDescription = root.path("error_description").asText();
            String reason = root.path("error").path("errors").path(0).path("reason").asText();
            if ("invalid_grant".equals(error)) {
                return prefix + " La autorizacion expiro o fue revocada. Conecta Google Drive nuevamente.";
            }
            if ("invalid_request".equals(error) && !errorDescription.isBlank()) {
                return prefix + " " + errorDescription;
            }
            if ("storageQuotaExceeded".equals(reason)) {
                return prefix + " No hay espacio suficiente disponible en tu Google Drive personal.";
            }

            JsonNode messageNode = root.path("error").path("message");
            if (!messageNode.asText().isBlank()) {
                return prefix + " " + messageNode.asText();
            }
            if (!error.isBlank()) {
                return prefix + " " + error;
            }
        } catch (Exception ignored) {
        }

        return prefix + " " + responseBody;
    }

    private String resolveRequiredClientSecret(String clientId) {
        return backupSettingsRepository.findAll().stream().findFirst()
                .map(BackupSettings::getGoogleOauthClientSecret)
                .filter(secret -> secret != null && !secret.isBlank())
                .orElseThrow(() -> new RemoteBackupException(
                        "Falta el Client Secret de Google OAuth para completar la conexion.",
                        false
                ));
    }

    private RemoteBackupException buildUnexpectedRemoteException(String fallbackMessage, Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof HttpTimeoutException) {
                return new RemoteBackupException("No hay conexion disponible para comunicarse con Google Drive.", ex, true);
            }
            if (current instanceof IllegalArgumentException) {
                return new RemoteBackupException(
                        "No se pudo preparar la solicitud a Google Drive. Vuelve a intentarlo.",
                        ex,
                        true
                );
            }
            current = current.getCause();
        }

        return new RemoteBackupException(fallbackMessage + " " + ex.getMessage(), ex, true);
    }

    private void cleanupExpiredSessions() {
        pendingOauthSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private String getRedirectUri() {
        return "http://127.0.0.1:" + serverPort + "/api/admin/backups/oauth/callback";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String randomUrlSafeToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el code_challenge para Google OAuth.", ex);
        }
    }

    private String buildCallbackHtml(boolean success, String message) {
        String escapedMessage = message
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String status = success ? "success" : "error";
        String title = success ? "Google Drive conectado" : "No se pudo conectar Google Drive";

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8" />
                  <title>%s</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #f5f7fb; color: #10233f; padding: 32px; }
                    .card { max-width: 520px; margin: 48px auto; background: white; border-radius: 18px; padding: 28px; box-shadow: 0 12px 40px rgba(16,35,63,.12); }
                    h1 { margin-top: 0; font-size: 24px; }
                    p { line-height: 1.5; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>%s</h1>
                    <p>%s</p>
                    <p>Puedes volver a la ventana principal de la aplicacion.</p>
                  </div>
                  <script>
                    if (window.opener) {
                      window.opener.postMessage({ type: 'google-drive-oauth', status: '%s', message: %s }, '*');
                    }
                    setTimeout(function () { window.close(); }, 1200);
                  </script>
                </body>
                </html>
                """.formatted(title, title, escapedMessage, status, toJsonString(message));
    }

    private String toJsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "\"\"";
        }
    }

    private record PendingOauthSession(Long settingsId, String codeVerifier, long createdAtMillis) {
        private boolean isExpired() {
            return System.currentTimeMillis() - createdAtMillis > OAUTH_STATE_TTL_MINUTES * 60_000;
        }
    }

    private record TokenResponse(String accessToken, String refreshToken) {
    }

    private record DriveFolder(String id, String name) {
    }
}
