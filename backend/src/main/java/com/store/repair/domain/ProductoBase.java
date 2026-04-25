package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "productos_base")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoBase extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El codigo base es obligatorio")
    @Column(name = "codigo_base", nullable = false, unique = true)
    private String codigoBase;

    @NotBlank(message = "El nombre base es obligatorio")
    @Column(name = "nombre_base", nullable = false)
    private String nombreBase;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaInventario categoria;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "marca_id", nullable = false)
    private MarcaInventario marca;

    @Column(name = "modelo")
    private String modelo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @OneToMany(mappedBy = "productoBase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("productoBase")
    @Builder.Default
    private List<ProductoBaseCompatibilidad> compatibilidades = new ArrayList<>();

    public void addCompatibilidad(ProductoBaseCompatibilidad compatibilidad) {
        if (compatibilidad == null) {
            return;
        }
        compatibilidades.add(compatibilidad);
        compatibilidad.setProductoBase(this);
    }

    public void replaceCompatibilidades(List<ProductoBaseCompatibilidad> nuevasCompatibilidades) {
        compatibilidades.clear();
        if (nuevasCompatibilidades == null) {
            return;
        }
        nuevasCompatibilidades.forEach(this::addCompatibilidad);
    }
}
