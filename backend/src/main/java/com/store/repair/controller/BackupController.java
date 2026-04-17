package com.store.repair.controller;

import com.store.repair.domain.BackupRecord;
import com.store.repair.dto.BackupResponse;
import com.store.repair.dto.BackupSettingsRequest;
import com.store.repair.dto.BackupSettingsResponse;
import com.store.repair.dto.BackupSummaryResponse;
import com.store.repair.service.BackupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

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
}
