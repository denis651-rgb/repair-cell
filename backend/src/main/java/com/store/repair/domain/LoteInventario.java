package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lotes_inventario")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteInventario extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private ProductoVariante variante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @NotBlank(message = "El codigo de lote es obligatorio")
    @Column(name = "codigo_lote", nullable = false, unique = true)
    private String codigoLote;

    @Column(name = "codigo_proveedor")
    private String codigoProveedor;

    @NotNull(message = "La fecha de ingreso es obligatoria")
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @NotNull(message = "La cantidad inicial es obligatoria")
    @Min(value = 0, message = "La cantidad inicial no puede ser negativa")
    @Column(name = "cantidad_inicial", nullable = false)
    private Integer cantidadInicial;

    @NotNull(message = "La cantidad disponible es obligatoria")
    @Min(value = 0, message = "La cantidad disponible no puede ser negativa")
    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;

    @NotNull(message = "El costo unitario es obligatorio")
    @Min(value = 0, message = "El costo unitario no puede ser negativo")
    @Column(name = "costo_unitario", nullable = false)
    private Double costoUnitario;

    @NotNull(message = "El subtotal de compra es obligatorio")
    @Min(value = 0, message = "El subtotal no puede ser negativo")
    @Column(name = "subtotal_compra", nullable = false)
    private Double subtotalCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoLoteInventario estado;

    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "visible_en_ventas", nullable = false)
    private Boolean visibleEnVentas;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "motivo_cierre")
    private String motivoCierre;
}
