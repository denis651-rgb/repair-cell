package com.store.repair.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RentabilidadDetalleLoteResponse {

    private Long loteId;
    private String codigoLote;
    private Long varianteId;
    private String codigoVariante;
    private Long productoBaseId;
    private String codigoBase;
    private String nombreBase;
    private String marcaNombre;
    private String categoriaNombre;
    private String modelo;
    private String calidad;
    private Integer cantidadVendida;
    private Double ventas;
    private Double costo;
    private Double ganancia;
}
