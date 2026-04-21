package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "productos_inventario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoInventario extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaInventario categoria;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "marca_id")
    private MarcaInventario marca;

    @NotBlank(message = "El SKU es obligatorio")
    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "calidad")
    private String calidad;

    @Min(value = 0, message = "El costo unitario no puede ser negativo")
    @Column(name = "costo_unitario", nullable = false)
    private Double costoUnitario;

    @Min(value = 0, message = "El precio de venta no puede ser negativo")
    @Column(name = "precio_venta", nullable = false)
    private Double precioVenta;

    @Min(value = 0, message = "La cantidad no puede ser negativa")
    @Column(name = "cantidad_stock", nullable = false)
    private Integer cantidadStock;

    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    @Column(name = "costo_promedio")
    @Builder.Default
    private Double costoPromedio = 0.0;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Transient
    @Builder.Default
    private Boolean skuEditable = Boolean.TRUE;
}
