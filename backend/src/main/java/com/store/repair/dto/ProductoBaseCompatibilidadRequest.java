package com.store.repair.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoBaseCompatibilidadRequest {

    private String marcaCompatible;
    private String modeloCompatible;
    private String codigoReferencia;
    private String nota;
    private Boolean activa;
}
