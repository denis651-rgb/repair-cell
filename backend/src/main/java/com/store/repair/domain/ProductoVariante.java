package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "productos_variantes")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVariante extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "producto_base_id", nullable = false)
    private ProductoBase productoBase;

    @NotBlank(message = "El codigo de variante es obligatorio")
    @Column(name = "codigo_variante", nullable = false, unique = true)
    private String codigoVariante;

    @NotBlank(message = "La calidad es obligatoria")
    @Column(name = "calidad", nullable = false)
    private String calidad;

    @Column(name = "tipo_presentacion")
    private String tipoPresentacion;

    @Column(name = "color")
    private String color;

    @Min(value = 0, message = "El precio sugerido no puede ser negativo")
    @Column(name = "precio_venta_sugerido", nullable = false)
    private Double precioVentaSugerido;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Transient
    @Builder.Default
    private Integer stockDisponibleTotal = 0;

    @Transient
    @Builder.Default
    private Integer lotesActivos = 0;
}
