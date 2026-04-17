package com.store.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BackupSettingsRequest {

    private boolean enabled;

    @NotBlank(message = "La expresión cron es obligatoria")
    private String cron;

    @NotBlank(message = "La carpeta local es obligatoria")
    private String directory;

    private boolean zipEnabled;

    @Min(value = 1, message = "La retención mínima es de 1 día")
    private int retentionDays;

    private boolean googleDriveEnabled;
    private String googleDriveFolderId;
    private String googleServiceAccountKeyPath;
}
