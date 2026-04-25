package com.store.repair.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    /**
     * Activa o desactiva la ejecución programada.
     */
    private boolean enabled = true;

    /**
     * Expresión cron del backup automático.
     * Ejemplo: todos los días a la 1 AM.
     */
    @NotBlank
    private String cron = "0 0 1 * * *";

    /**
     * Carpeta donde se guardan los backups.
     */
    @NotBlank
    private String directory = AppStoragePaths.resolveBackupDirectory();

    /**
     * Si true, comprime el backup en zip.
     */
    private boolean zipEnabled = true;

    /**
     * Cuántos días conservar los backups.
     */
    @Min(1)
    private int retentionDays = 30;

    /**
     * Habilita la subida remota a Google Drive.
     */
    private boolean googleDriveEnabled = false;

    /**
     * ID de la carpeta compartida donde se suben los respaldos.
     */
    private String googleDriveFolderId = "";

    /**
     * Ruta local al archivo JSON de la service account de Google.
     */
    private String googleServiceAccountKeyPath = "";
}
