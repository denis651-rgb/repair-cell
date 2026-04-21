package com.store.repair.dto;

public record ReporteClienteGlobalResponse(
        Long clienteId,
        String cliente,
        long totalOrdenes,
        double totalReparaciones,
        double totalVentas,
        double totalConsumidoGlobal,
        double saldoPendiente,
        double totalAbonado
) {
}
