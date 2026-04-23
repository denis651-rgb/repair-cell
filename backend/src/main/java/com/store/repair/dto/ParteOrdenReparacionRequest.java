package com.store.repair.dto;

import com.store.repair.domain.TipoFuenteParte;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ParteOrdenReparacionRequest {
    private Long productoId;
    private Long varianteId;
    private String nombreParte;

    @Min(value = 1, message = "La cantidad debe ser mayor a cero")
    private Integer cantidad;

    @Min(value = 0, message = "El costo unitario no puede ser negativo")
    private Double costoUnitario;

    @Min(value = 0, message = "El precio unitario no puede ser negativo")
    private Double precioUnitario;

    private TipoFuenteParte tipoFuente;
    private String notas;
}
