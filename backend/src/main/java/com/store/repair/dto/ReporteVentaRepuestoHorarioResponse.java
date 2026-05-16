package com.store.repair.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class ReporteVentaRepuestoHorarioResponse {

    private Long ventaId;
    private Long detalleId;
    private LocalDate fechaVenta;
    private LocalTime horaVenta;
    private LocalDateTime registradoEn;
    private String numeroComprobante;
    private String cliente;
    private String codigoVariante;
    private String nombreProducto;
    private String marca;
    private String modelo;
    private String calidad;
    private String tipoPresentacion;
    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal;
}
