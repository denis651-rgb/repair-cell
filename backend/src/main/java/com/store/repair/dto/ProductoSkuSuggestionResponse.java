package com.store.repair.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoSkuSuggestionResponse {
    private String skuSugerido;
    private String skuNormalizado;
    private Boolean skuValido;
    private List<ProductoInventarioResumenDto> productosSimilares;
}
