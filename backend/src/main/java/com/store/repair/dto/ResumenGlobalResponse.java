package com.store.repair.dto;

public record ResumenGlobalResponse(
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
        double balanceNeto
) {
}
