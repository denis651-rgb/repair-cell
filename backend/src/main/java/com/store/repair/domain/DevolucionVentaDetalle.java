package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "devoluciones_venta_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionVentaDetalle extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucion_venta_id", nullable = false)
    @JsonIgnoreProperties("detalles")
    private DevolucionVenta devolucionVenta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venta_detalle_id", nullable = false)
    @JsonIgnoreProperties({ "venta", "detallesLote", "hibernateLazyInitializer", "handler" })
    private VentaDetalle ventaDetalle;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_venta_unitario", nullable = false)
    private Double precioVentaUnitario;

    @Column(nullable = false)
    private Double subtotal;

    @OneToMany(mappedBy = "devolucionDetalle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("devolucionDetalle")
    @Builder.Default
    private List<DevolucionVentaDetalleLote> detallesLote = new ArrayList<>();

    public void addDetalleLote(DevolucionVentaDetalleLote detalleLote) {
        if (detalleLote == null) {
            return;
        }

        detallesLote.add(detalleLote);
        detalleLote.setDevolucionDetalle(this);
    }

    public void replaceDetallesLote(List<DevolucionVentaDetalleLote> nuevosDetalles) {
        detallesLote.clear();

        if (nuevosDetalles == null) {
            return;
        }

        nuevosDetalles.forEach(this::addDetalleLote);
    }
}