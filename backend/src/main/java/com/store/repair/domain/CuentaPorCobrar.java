package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "cuentas_por_cobrar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaPorCobrar extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, unique = true)
    private Venta venta;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "monto_original", nullable = false)
    private Double montoOriginal;

    @Column(name = "saldo_pendiente", nullable = false)
    private Double saldoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCuentaPorCobrar estado;

    @OneToMany(mappedBy = "cuentaPorCobrar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("cuentaPorCobrar")
    @Builder.Default
    private List<AbonoCuentaPorCobrar> abonos = new ArrayList<>();
}
