package com.store.repair.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoInventarioResumenDto {
    private Long id;
    private String sku;
    private String nombre;
    private String categoria;
    private String marca;
    private String calidad;
    private Boolean skuEditable;
}
