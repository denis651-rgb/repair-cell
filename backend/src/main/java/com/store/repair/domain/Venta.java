package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "ventas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta;

    @Column(name = "numero_comprobante")
    private String numeroComprobante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false)
    private TipoPagoVenta tipoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVenta estado;

    @Column(nullable = false)
    @Builder.Default
    private Double total = 0D;

    private String observaciones;

    @Column(name = "fecha_devolucion")
    private LocalDate fechaDevolucion;

    @Column(name = "motivo_devolucion")
    private String motivoDevolucion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("venta")
    @Builder.Default
    private List<VentaDetalle> detalles = new ArrayList<>();

    public void addDetalle(VentaDetalle detalle) {
        if (detalle == null) {
            return;
        }
        detalles.add(detalle);
        detalle.setVenta(this);
    }

    public void replaceDetalles(List<VentaDetalle> nuevosDetalles) {
        detalles.clear();
        if (nuevosDetalles == null) {
            return;
        }
        nuevosDetalles.forEach(this::addDetalle);
    }
}
