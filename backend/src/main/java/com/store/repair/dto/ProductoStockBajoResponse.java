package com.store.repair.dto;

public record ProductoStockBajoResponse(Long id, String nombre, int stockActual, int stockMinimo) {
}
