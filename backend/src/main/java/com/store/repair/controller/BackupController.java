package com.store.repair.controller;

import com.store.repair.domain.BackupRecord;
import com.store.repair.dto.BackupResponse;
import com.store.repair.dto.BackupSettingsRequest;
import com.store.repair.dto.BackupSettingsResponse;
import com.store.repair.dto.BackupSummaryResponse;
import com.store.repair.dto.DriveConnectionTestResponse;
import com.store.repair.dto.GoogleOAuthStartResponse;
import com.store.repair.dto.RemoteBackupFileResponse;
import com.store.repair.dto.RestoreExecuteRequest;
import com.store.repair.dto.RestoreExecutionResponse;
import com.store.repair.dto.RestoreLastResultResponse;
import com.store.repair.dto.RestoreLocalValidationResponse;
import com.store.repair.dto.RestoreRemoteValidationRequest;
import com.store.repair.service.BackupRestoreService;
import com.store.repair.service.BackupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final BackupRestoreService backupRestoreService;

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupResponse> runBackupNow() {
        return ResponseEntity.ok(backupService.performManualBackup());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<BackupRecord>> listBackups(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano
    ) {
        return ResponseEntity.ok(backupService.listBackups(pagina, tamano));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupSettingsResponse> getSettings() {
        return ResponseEntity.ok(backupService.getSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupSettingsResponse> updateSettings(@Valid @RequestBody BackupSettingsRequest request) {
        return ResponseEntity.ok(backupService.updateSettings(request));
    }

    @PostMapping("/oauth/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GoogleOAuthStartResponse> startGoogleOAuth(@Valid @RequestBody BackupSettingsRequest request) {
        return ResponseEntity.ok(backupService.startGoogleDriveOAuth(request));
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> completeGoogleOAuth(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription
    ) {
        return ResponseEntity.ok(backupService.completeGoogleDriveOAuth(state, code, error, errorDescription));
    }

    @PostMapping("/oauth/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disconnectGoogleOAuth() {
        backupService.disconnectGoogleDrive();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-drive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DriveConnectionTestResponse> testDrive() {
        return ResponseEntity.ok(backupService.testDriveConnection());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupSummaryResponse> getSummary() {
        return ResponseEntity.ok(backupService.getSummary());
    }

    @PostMapping("/retry-pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Integer> retryPendingUploads() {
        return ResponseEntity.ok(backupService.retryPendingUploads());
    }

    @PostMapping(value = "/restore/validate-local", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestoreLocalValidationResponse> validateLocalRestore(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(backupRestoreService.validateLocalBackup(file));
    }

    @GetMapping("/restore/drive/files")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RemoteBackupFileResponse>> listDriveRestoreFiles() {
        return ResponseEntity.ok(backupRestoreService.listRemoteBackups());
    }

    @PostMapping("/restore/validate-drive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestoreLocalValidationResponse> validateDriveRestore(
            @Valid @RequestBody RestoreRemoteValidationRequest request) {
        return ResponseEntity.ok(backupRestoreService.validateRemoteBackup(request.getFileId()));
    }

    @PostMapping("/restore/execute-local")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestoreExecutionResponse> executeLocalRestore(@Valid @RequestBody RestoreExecuteRequest request) {
        return ResponseEntity.ok(backupRestoreService.executePreparedRestore(request.getSessionId()));
    }

    @PostMapping("/restore/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestoreExecutionResponse> executeRestore(@Valid @RequestBody RestoreExecuteRequest request) {
        return ResponseEntity.ok(backupRestoreService.executePreparedRestore(request.getSessionId()));
    }

    @GetMapping("/restore/last-result")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestoreLastResultResponse> getLastRestoreResult() {
        return ResponseEntity.ok(backupRestoreService.getLastRestoreResult());
    }
}
