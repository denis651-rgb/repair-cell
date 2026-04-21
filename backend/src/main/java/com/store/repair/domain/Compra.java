package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "compras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compra extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @NotNull(message = "La fecha de compra es obligatoria")
    @Column(name = "fecha_compra", nullable = false)
    private LocalDate fechaCompra;

    @Column(name = "numero_comprobante")
    private String numeroComprobante;

    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false)
    private TipoPagoCompra tipoPago;

    @Column(nullable = false)
    @Builder.Default
    private Double total = 0D;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = Boolean.TRUE;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("compra")
    @Builder.Default
    private List<CompraDetalle> detalles = new ArrayList<>();

    public void addDetalle(CompraDetalle detalle) {
        if (detalle == null) {
            return;
        }
        detalles.add(detalle);
        detalle.setCompra(this);
    }

    public void replaceDetalles(List<CompraDetalle> nuevosDetalles) {
        detalles.clear();
        if (nuevosDetalles == null) {
            return;
        }
        nuevosDetalles.forEach(this::addDetalle);
    }
}
