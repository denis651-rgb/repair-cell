package com.store.repair.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class LoteVentaOptionResponse {

    private Long id;
    private Long varianteId;
    private String codigoLote;
    private String codigoProveedor;
    private LocalDate fechaIngreso;
    private Integer cantidadDisponible;
    private Double costoUnitario;
    private Double precioVentaUnitario;
    private Double gananciaUnitaria;
    private String proveedorNombre;
}
