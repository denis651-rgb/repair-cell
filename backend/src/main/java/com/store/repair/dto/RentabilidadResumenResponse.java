package com.store.repair.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RentabilidadResumenResponse {

    private Double totalVendido;
    private Double costoTotal;
    private Double gananciaBruta;
    private Double margenPorcentaje;
}
