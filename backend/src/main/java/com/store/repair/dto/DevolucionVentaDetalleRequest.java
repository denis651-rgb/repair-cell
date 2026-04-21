package com.store.repair.dto;

import lombok.Data;

@Data
public class DevolucionVentaDetalleRequest {

    private Long ventaDetalleId;

    private Integer cantidad;
}
