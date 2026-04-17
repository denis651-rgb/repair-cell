
package com.store.repair.dto;

public record ReporteResumenResponse(
    long totalClientes,
    long totalOrdenes,
    long ordenesPendientes,
    long inventarioBajo
) {}
