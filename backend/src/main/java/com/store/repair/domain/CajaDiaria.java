package com.store.repair.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cajas_diarias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaDiaria extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fechaApertura;

    private LocalDateTime fechaCierre;

    @Column(nullable = false)
    private Double montoApertura;

    private Double montoCierre;

    private Double montoEsperado; // Ingresos + Apertura

    @Column(nullable = false)
    private String estado; // ABIERTA, CERRADA

    @Column(nullable = false)
    private String usuarioApertura;

    private String usuarioCierre;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}
