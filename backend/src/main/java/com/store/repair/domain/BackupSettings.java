package com.store.repair.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupSettings extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private String cron;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String directory;

    @Column(nullable = false)
    private Boolean zipEnabled;

    @Column(nullable = false)
    private Integer retentionDays;

    @Column(nullable = false)
    @Builder.Default
    private Boolean googleDriveEnabled = false;

    @Column(columnDefinition = "TEXT")
    private String googleDriveFolderId;

    @Column(columnDefinition = "TEXT")
    private String googleServiceAccountKeyPath;

    @Column(columnDefinition = "TEXT")
    private String googleDriveFolderName;

    @Column(columnDefinition = "TEXT")
    private String googleOauthClientId;

    @Column(columnDefinition = "TEXT")
    private String googleOauthClientSecret;

    @Column(columnDefinition = "TEXT")
    private String googleOauthRefreshToken;

    private LocalDateTime googleOauthConnectedAt;

    private LocalDateTime lastAutomaticBackupAt;
}
