package com.store.repair.dto;

public record ClienteMontoAcumuladoDto(
        Long clienteId,
        String cliente,
        double monto
) {
}
