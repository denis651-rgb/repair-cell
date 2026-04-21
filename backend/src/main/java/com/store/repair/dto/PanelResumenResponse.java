package com.store.repair.dto;

import java.util.List;

public record PanelResumenResponse(
        long totalClientes,
        long totalOrdenes,
        long ordenesPendientes,
        long totalVentas,
        long totalCompras,
        long cuentasPorCobrarAbiertas,
        double saldoPendienteCobro,
        long inventarioBajo,
        double ingresosTotales,
        double egresosTotales,
        double balanceNeto,
        List<SerieDiariaResponse> operacionesPorDia,
        List<SerieDiariaResponse> ingresosPorDia,
        List<SerieDiariaResponse> egresosPorDia,
        List<ReporteEstadoResponse> estadosOrden,
        List<ProductoStockBajoResponse> productosStockBajo,
        double ingresosReparaciones,
        double ingresosVentas,
        double cobrosCredito,
        double egresosCompras,
        double egresosManuales
) {
}
