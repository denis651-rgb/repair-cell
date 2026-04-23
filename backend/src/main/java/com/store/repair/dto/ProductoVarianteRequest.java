package com.store.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoVarianteRequest {

    @NotNull(message = "El producto base es obligatorio")
    private Long productoBaseId;

    @NotBlank(message = "El codigo de variante es obligatorio")
    private String codigoVariante;

    @NotBlank(message = "La calidad es obligatoria")
    private String calidad;

    private String tipoPresentacion;
    private String color;

    @NotNull(message = "El precio de venta sugerido es obligatorio")
    @Min(value = 0, message = "El precio sugerido no puede ser negativo")
    private Double precioVentaSugerido;

    private Boolean activo;
}
