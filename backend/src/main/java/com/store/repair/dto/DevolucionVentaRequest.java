package com.store.repair.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DevolucionVentaRequest {

    private LocalDate fechaDevolucion;

    private String motivoDevolucion;

    private List<DevolucionVentaDetalleRequest> detalles;
}
