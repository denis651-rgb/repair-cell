package com.store.repair.dto;

public record CajaResumenActualResponse(
        double entradas,
        double salidas,
        long movimientos,
        double esperado) {
}
