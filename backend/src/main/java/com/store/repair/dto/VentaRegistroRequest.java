package com.store.repair.dto;

import com.store.repair.domain.TipoPagoVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class VentaRegistroRequest {

    @NotNull(message = "El cliente es obligatorio")
    private Long clienteId;

    private LocalDate fechaVenta;

    private String numeroComprobante;

    private String observaciones;

    @NotNull(message = "El tipo de pago es obligatorio")
    private TipoPagoVenta tipoPago;

    @Valid
    private List<VentaDetalleRegistroRequest> detalles = new ArrayList<>();
}
