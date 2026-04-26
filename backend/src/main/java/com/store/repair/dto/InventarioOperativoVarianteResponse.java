package com.store.repair.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InventarioOperativoVarianteResponse {

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
    private Double precioVentaSugerido;
    private Integer stockMinimo;
    private Integer stockDisponibleTotal;
    private Boolean stockBajo;
    private Integer faltanteReposicion;
    private Integer lotesActivos;
    private List<LoteInventarioHistorialResponse> lotesOperativos;
}
