package com.store.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VentaDetalleRegistroRequest {

    @NotNull(message = "La variante es obligatoria")
    private Long varianteId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a cero")
    private Integer cantidad;

    private Double precioListaUnitario;

    private Double precioVentaUnitario;
}
