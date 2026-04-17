package com.store.repair.dto;

import java.util.List;

public record PanelResumenResponse(
        long totalClientes,
        long totalOrdenes,
        long ordenesPendientes,
        long inventarioBajo,
        double ingresosTotales,
        List<SerieDiariaResponse> ordenesPorDia,
        List<SerieDiariaResponse> ingresosPorDia,
        List<ReporteEstadoResponse> estados,
        List<ProductoStockBajoResponse> productosStockBajo
) {
}
