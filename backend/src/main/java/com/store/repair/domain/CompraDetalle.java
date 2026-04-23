package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "compras_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraDetalle extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    @JsonIgnoreProperties("detalles")
    private Compra compra;

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

    @Column(name = "codigo_lote")
    private String codigoLote;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_compra_unitario", nullable = false)
    private Double precioCompraUnitario;

    @Column(name = "precio_venta_unitario", nullable = false)
    private Double precioVentaUnitario;

    @Column(nullable = false)
    private Double subtotal;
}
