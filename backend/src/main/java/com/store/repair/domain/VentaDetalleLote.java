package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "venta_detalle_lote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDetalleLote extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_detalle_id", nullable = false)
    @JsonIgnoreProperties("detallesLote")
    private VentaDetalle ventaDetalle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteInventario lote;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "cantidad_devuelta", nullable = false)
    @Builder.Default
    private Integer cantidadDevuelta = 0;

    @Column(name = "costo_unitario_aplicado", nullable = false)
    private Double costoUnitarioAplicado;

    @Column(name = "costo_total", nullable = false)
    private Double costoTotal;

    @Column(name = "ganancia_bruta", nullable = false)
    private Double gananciaBruta;
}
