package com.store.repair.dto;

import java.time.LocalDate;

public interface RentabilidadMovimientoProjection {

    Long getLoteId();

    String getCodigoLote();

    Long getVarianteId();

    String getCodigoVariante();

    Long getProductoBaseId();

    String getCodigoBase();

    String getNombreBase();

    String getMarcaNombre();

    String getCategoriaNombre();

    String getModelo();

    String getCalidad();

    LocalDate getFechaVenta();

    Integer getCantidad();

    Integer getCantidadDevuelta();

    Double getPrecioVentaUnitario();

    Double getCostoUnitarioAplicado();
}
