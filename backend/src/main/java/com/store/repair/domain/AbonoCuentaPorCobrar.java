package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "abonos_cuentas_por_cobrar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbonoCuentaPorCobrar extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_por_cobrar_id", nullable = false)
    @JsonIgnoreProperties("abonos")
    private CuentaPorCobrar cuentaPorCobrar;

    @Column(name = "fecha_abono", nullable = false)
    private LocalDate fechaAbono;

    @Column(nullable = false)
    private Double monto;

    private String observaciones;
}
