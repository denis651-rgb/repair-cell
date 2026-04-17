package com.store.repair.dto;

public record ReporteClienteResponse(Long clienteId, String cliente, long totalOrdenes, double totalFacturado) {
}
