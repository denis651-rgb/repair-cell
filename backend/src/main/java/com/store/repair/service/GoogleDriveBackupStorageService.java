package com.store.repair.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.repair.domain.BackupSettings;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleDriveBackupStorageService implements RemoteBackupStorageService {

    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Override
    public String upload(Path file, BackupSettings settings) {
        if (settings == null || !Boolean.TRUE.equals(settings.getGoogleDriveEnabled())) {
            throw new RemoteBackupException("Google Drive no está habilitado en la configuración", false);
        }

        if (settings.getGoogleServiceAccountKeyPath() == null || settings.getGoogleServiceAccountKeyPath().isBlank()) {
            throw new RemoteBackupException("Falta la ruta del archivo de credenciales de Google", false);
        }

        if (settings.getGoogleDriveFolderId() == null || settings.getGoogleDriveFolderId().isBlank()) {
            throw new RemoteBackupException("Falta el ID de la carpeta de Google Drive", false);
        }

        Path credentialsPath = Path.of(settings.getGoogleServiceAccountKeyPath()).toAbsolutePath().normalize();
        if (!Files.exists(credentialsPath)) {
            throw new RemoteBackupException("No existe el archivo de credenciales en la ruta indicada", false);
        }

        try {
            JsonNode credentials = objectMapper.readTree(Files.readString(credentialsPath));
            String accessToken = requestAccessToken(credentials);
            return uploadFile(file, accessToken, settings.getGoogleDriveFolderId());
        } catch (RemoteBackupException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new RemoteBackupException("No se pudo leer el archivo de credenciales de Google Drive", ex, false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RemoteBackupException("La subida a Google Drive fue interrumpida", ex, true);
        } catch (Exception ex) {
            throw new RemoteBackupException("No se pudo subir el backup a Google Drive: " + ex.getMessage(), ex, true);
        }
    }

    private String requestAccessToken(JsonNode credentials) throws Exception {
        String clientEmail = credentials.path("client_email").asText();
        String privateKeyPem = credentials.path("private_key").asText();
        String tokenUri = credentials.path("token_uri").asText("https://oauth2.googleapis.com/token");

        if (clientEmail.isBlank() || privateKeyPem.isBlank()) {
            throw new RemoteBackupException("Las credenciales de Google Drive están incompletas", false);
        }

        Instant now = Instant.now();
        String assertion = Jwts.builder()
                .issuer(clientEmail)
                .subject(clientEmail)
                .audience().add(tokenUri).and()
                .claim("scope", DRIVE_SCOPE)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(3600)))
                .signWith(parsePrivateKey(privateKeyPem), SignatureAlgorithm.RS256)
                .compact();

        String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUri))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException("Google OAuth rechazó la autenticación: " + response.body(), retryable);
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        String accessToken = tokenResponse.path("access_token").asText();
        if (accessToken.isBlank()) {
            throw new RemoteBackupException("Google OAuth no devolvió access_token", false);
        }
        return accessToken;
    }

    private String uploadFile(Path file, String accessToken, String folderId) throws IOException, InterruptedException {
        String boundary = "backup-" + UUID.randomUUID();
        String metadataJson = objectMapper.writeValueAsString(Map.of(
                "name", file.getFileName().toString(),
                "parents", new String[] { folderId }
        ));

        byte[] fileBytes = Files.readAllBytes(file);
        byte[] body = buildMultipartBody(boundary, metadataJson, fileBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new RemoteBackupException("Google Drive rechazó la subida: " + response.body(), retryable);
        }

        JsonNode driveResponse = objectMapper.readTree(response.body());
        String fileId = driveResponse.path("id").asText();
        if (fileId.isBlank()) {
            throw new RemoteBackupException("Google Drive no devolvió el ID del archivo subido", true);
        }

        return "https://drive.google.com/file/d/" + fileId + "/view";
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

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String sanitized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(sanitized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }
}
