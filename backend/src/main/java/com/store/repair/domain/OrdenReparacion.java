package com.store.repair.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.store.repair.util.OrdenMontoUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(name = "ordenes_reparacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenReparacion extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_orden", nullable = false, unique = true)
    private String numeroOrden;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dispositivo_id")
    private Dispositivo dispositivo;

    @Column(name = "problema_reportado", nullable = false, columnDefinition = "TEXT")
    private String problemaReportado;

    @Column(name = "diagnostico_tecnico", columnDefinition = "TEXT")
    private String diagnosticoTecnico;

    @Column(name = "tecnico_responsable")
    private String tecnicoResponsable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReparacion estado;

    @Column(name = "costo_estimado", nullable = false)
    private Double costoEstimado;

    @Column(name = "costo_final", nullable = false)
    private Double costoFinal;

    @Column(name = "fecha_entrega_estimada")
    private LocalDate fechaEntregaEstimada;

    @Column(name = "recibido_en", nullable = false)
    private LocalDateTime recibidoEn;

    @Column(name = "entregado_en")
    private LocalDateTime entregadoEn;

    @Column(name = "dias_garantia", nullable = false)
    private Integer diasGarantia;

    @Column(name = "nombre_firma_cliente")
    private String nombreFirmaCliente;

    @Column(name = "texto_confirmacion", columnDefinition = "TEXT")
    private String textoConfirmacion;

    @OneToMany(mappedBy = "ordenReparacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ParteOrdenReparacion> partes = new ArrayList<>();

    @Transient
    public Double getMontoPartes() {
        return OrdenMontoUtils.resolveMontoPartes(partes);
    }

    @Transient
    public Double getMontoVisible() {
        return OrdenMontoUtils.resolveMontoVisible(this);
    }

    @Transient
    public Double getCostoFinalCalculado() {
        double costoFinalActual = this.costoFinal == null ? 0D : this.costoFinal;
        return costoFinalActual > 0 ? costoFinalActual : getMontoVisible();
    }
}
