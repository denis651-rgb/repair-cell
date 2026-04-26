package com.store.repair.dto;

public record ProductoStockBajoResponse(
        Long id,
        Long productoBaseId,
        String nombre,
        String codigoVariante,
        String marcaNombre,
        String modelo,
        int stockActual,
        int stockMinimo,
        int faltanteReposicion
) {
}
