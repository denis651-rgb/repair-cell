package com.store.repair.dto;

import com.store.repair.domain.EstadoVenta;
import com.store.repair.domain.TipoPagoVenta;

import java.time.LocalDate;

public record VentaListadoResponse(
        Long id,
        LocalDate fechaVenta,
        String numeroComprobante,
        String clienteNombre,
        TipoPagoVenta tipoPago,
        EstadoVenta estado,
        Double total
) {
}
