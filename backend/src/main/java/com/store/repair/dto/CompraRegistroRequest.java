package com.store.repair.dto;

import com.store.repair.domain.TipoPagoCompra;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CompraRegistroRequest {

    @NotNull(message = "El proveedor es obligatorio")
    private Long proveedorId;

    private LocalDate fechaCompra;

    private String numeroComprobante;

    private String observaciones;

    @NotNull(message = "El tipo de pago es obligatorio")
    private TipoPagoCompra tipoPago;

    @Valid
    private List<CompraDetalleRegistroRequest> detalles = new ArrayList<>();
}
