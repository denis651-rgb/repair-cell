package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "devoluciones_venta_detalle_lote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionVentaDetalleLote extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucion_detalle_id", nullable = false)
    @JsonIgnoreProperties("detallesLote")
    private DevolucionVentaDetalle devolucionDetalle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venta_detalle_lote_id", nullable = false)
    @JsonIgnoreProperties({ "ventaDetalle", "hibernateLazyInitializer", "handler" })
    private VentaDetalleLote ventaDetalleLote;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteInventario lote;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "costo_unitario_aplicado", nullable = false)
    private Double costoUnitarioAplicado;

    @Column(name = "costo_total", nullable = false)
    private Double costoTotal;

    @Column(name = "ganancia_revertida", nullable = false)
    private Double gananciaRevertida;
}