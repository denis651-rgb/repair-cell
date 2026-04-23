package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "ventas_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDetalle extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    @JsonIgnoreProperties("detalles")
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id")
    private ProductoInventario producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variante_id")
    private ProductoVariante variante;

    @Column(name = "categoria_nombre", nullable = false)
    private String categoriaNombre;

    @Column(nullable = false)
    private String sku;

    @Column(name = "nombre_producto", nullable = false)
    private String nombreProducto;

    @Column(name = "producto_base_codigo")
    private String productoBaseCodigo;

    @Column(nullable = false)
    private String marca;

    private String calidad;

    @Column(name = "tipo_presentacion")
    private String tipoPresentacion;

    @Column(name = "color")
    private String color;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "cantidad_devuelta", nullable = false)
    @Builder.Default
    private Integer cantidadDevuelta = 0;

    @Column(name = "precio_lista_unitario", nullable = false)
    private Double precioListaUnitario;

    @Column(name = "precio_venta_unitario", nullable = false)
    private Double precioVentaUnitario;

    @Column(nullable = false)
    private Double subtotal;

    @OneToMany(mappedBy = "ventaDetalle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("ventaDetalle")
    @Builder.Default
    private List<VentaDetalleLote> detallesLote = new ArrayList<>();

    public void addDetalleLote(VentaDetalleLote detalleLote) {
        if (detalleLote == null) {
            return;
        }
        detallesLote.add(detalleLote);
        detalleLote.setVentaDetalle(this);
    }

    public void replaceDetallesLote(List<VentaDetalleLote> nuevosDetalles) {
        detallesLote.clear();
        if (nuevosDetalles == null) {
            return;
        }
        nuevosDetalles.forEach(this::addDetalleLote);
    }
}
