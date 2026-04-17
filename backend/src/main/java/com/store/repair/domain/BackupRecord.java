package com.store.repair.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupRecord extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackupOrigen origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackupEstado estado;

    @Column(nullable = false)
    private String archivo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rutaLocal;

    @Column(columnDefinition = "TEXT")
    private String ubicacionRemota;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    private Long tamanoBytes;

    @Column(nullable = false)
    private LocalDateTime generadoEn;

    private LocalDateTime ultimoIntentoSubidaEn;

    @Column(nullable = false)
    @Builder.Default
    private Integer intentosSubida = 0;
}
