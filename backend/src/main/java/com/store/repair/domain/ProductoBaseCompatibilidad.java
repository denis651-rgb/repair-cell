package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "productos_base_compatibilidades")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoBaseCompatibilidad extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_base_id", nullable = false)
    @JsonIgnoreProperties("compatibilidades")
    private ProductoBase productoBase;

    @Column(name = "marca_compatible", nullable = false)
    private String marcaCompatible;

    @Column(name = "modelo_compatible", nullable = false)
    private String modeloCompatible;

    @Column(name = "codigo_referencia")
    private String codigoReferencia;

    @Column(name = "nota")
    private String nota;

    @Column(name = "activa", nullable = false)
    private Boolean activa;
}
