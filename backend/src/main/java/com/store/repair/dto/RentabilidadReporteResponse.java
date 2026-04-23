package com.store.repair.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RentabilidadReporteResponse {

    private RentabilidadResumenResponse resumen;
    private List<RentabilidadDetalleLoteResponse> porLote;
    private List<RentabilidadVarianteResponse> porVariante;
    private List<RentabilidadProductoBaseResponse> porProductoBase;
}
