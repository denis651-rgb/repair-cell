package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "partes_orden_reparacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParteOrdenReparacion extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "orden_reparacion_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private OrdenReparacion ordenReparacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id")
    private ProductoInventario producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variante_id")
    private ProductoVariante variante;

    @Column(name = "nombre_parte", nullable = false)
    private String nombreParte;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "costo_unitario", nullable = false)
    private Double costoUnitario;

    @Column(name = "precio_unitario", nullable = false)
    private Double precioUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_fuente", nullable = false)
    private TipoFuenteParte tipoFuente;

    @Column(name = "notas")
    private String notas;
}
