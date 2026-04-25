package com.store.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompraDetalleRegistroRequest {

    private Long productoBaseId;

    private Long varianteId;

    private Long productoId;

    private Long categoriaId;

    private Long marcaId;

    private String sku;

    private String nombreProducto;

    private String calidad;

    private String codigoProveedor;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a cero")
    private Integer cantidad;

    @NotNull(message = "El precio de compra es obligatorio")
    @Min(value = 0, message = "El precio de compra no puede ser negativo")
    private Double precioCompraUnitario;

    private Double precioVentaUnitario;
}
