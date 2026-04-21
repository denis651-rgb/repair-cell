package com.store.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompraDetalleRegistroRequest {

    private Long productoId;

    private Long categoriaId;

    private Long marcaId;

    private String sku;

    @NotBlank(message = "El nombre del producto es obligatorio")
    private String nombreProducto;

    private String calidad;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a cero")
    private Integer cantidad;

    @NotNull(message = "El precio de compra es obligatorio")
    @Min(value = 0, message = "El precio de compra no puede ser negativo")
    private Double precioCompraUnitario;

    @NotNull(message = "El precio de venta es obligatorio")
    @Min(value = 0, message = "El precio de venta no puede ser negativo")
    private Double precioVentaUnitario;
}
