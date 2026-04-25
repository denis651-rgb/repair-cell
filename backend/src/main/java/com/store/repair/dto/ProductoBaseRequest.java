package com.store.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProductoBaseRequest {

    private String codigoBase;

    @NotBlank(message = "El nombre base es obligatorio")
    private String nombreBase;

    @NotNull(message = "La categoria es obligatoria")
    private Long categoriaId;

    @NotNull(message = "La marca es obligatoria")
    private Long marcaId;

    private String modelo;
    private String descripcion;
    private Boolean activo;
    private List<ProductoBaseCompatibilidadRequest> compatibilidades = new ArrayList<>();
}
