package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "devoluciones_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionVenta extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    @JsonIgnoreProperties({ "detalles", "hibernateLazyInitializer", "handler" })
    private Venta venta;

    @Column(name = "fecha_devolucion", nullable = false)
    private LocalDate fechaDevolucion;

    @Column(name = "motivo_devolucion", nullable = false)
    private String motivoDevolucion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago_venta", nullable = false)
    private TipoPagoVenta tipoPagoVenta;

    @Column(name = "monto_total", nullable = false)
    @Builder.Default
    private Double montoTotal = 0D;

    @Column(name = "monto_aplicado_cuenta_por_cobrar", nullable = false)
    @Builder.Default
    private Double montoAplicadoCuentaPorCobrar = 0D;

    @Column(name = "monto_reembolsado", nullable = false)
    @Builder.Default
    private Double montoReembolsado = 0D;

    @OneToMany(mappedBy = "devolucionVenta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("devolucionVenta")
    @Builder.Default
    private List<DevolucionVentaDetalle> detalles = new ArrayList<>();

    public void addDetalle(DevolucionVentaDetalle detalle) {
        if (detalle == null) {
            return;
        }

        detalles.add(detalle);
        detalle.setDevolucionVenta(this);
    }

    public void replaceDetalles(List<DevolucionVentaDetalle> nuevosDetalles) {
        detalles.clear();

        if (nuevosDetalles == null) {
            return;
        }

        nuevosDetalles.forEach(this::addDetalle);
    }
}