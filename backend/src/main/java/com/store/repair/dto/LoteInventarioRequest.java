package com.store.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LoteInventarioRequest {

    @NotNull(message = "La variante es obligatoria")
    private Long varianteId;

    private Long proveedorId;

    @NotBlank(message = "El codigo de lote es obligatorio")
    private String codigoLote;

    private String codigoProveedor;

    @NotNull(message = "La fecha de ingreso es obligatoria")
    private LocalDate fechaIngreso;

    @NotNull(message = "La cantidad inicial es obligatoria")
    @Min(value = 0, message = "La cantidad inicial no puede ser negativa")
    private Integer cantidadInicial;

    @NotNull(message = "La cantidad disponible es obligatoria")
    @Min(value = 0, message = "La cantidad disponible no puede ser negativa")
    private Integer cantidadDisponible;

    @NotNull(message = "El costo unitario es obligatorio")
    @Min(value = 0, message = "El costo unitario no puede ser negativo")
    private Double costoUnitario;

    private Double subtotalCompra;
    private Long compraId;
    private Boolean activo;
    private Boolean visibleEnVentas;
    private String motivoCierre;
}
