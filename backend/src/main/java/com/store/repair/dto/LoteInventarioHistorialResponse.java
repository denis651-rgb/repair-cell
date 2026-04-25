package com.store.repair.dto;

import com.store.repair.domain.EstadoLoteInventario;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LoteInventarioHistorialResponse {

    private Long id;
    private Long varianteId;
    private String codigoVariante;
    private Long productoBaseId;
    private String codigoBase;
    private String nombreBase;
    private String categoriaNombre;
    private String marcaNombre;
    private String modelo;
    private String calidad;
    private String tipoPresentacion;
    private String color;
    private Long proveedorId;
    private String proveedorNombre;
    private String codigoLote;
    private String codigoProveedor;
    private LocalDate fechaIngreso;
    private LocalDateTime fechaCierre;
    private Double costoUnitario;
    private Double subtotalCompra;
    private Integer cantidadInicial;
    private Integer cantidadVendida;
    private Integer cantidadRestante;
    private EstadoLoteInventario estado;
    private Boolean activo;
    private Boolean visibleEnVentas;
    private Long compraId;
    private String motivoCierre;
}
