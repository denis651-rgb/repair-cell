package com.store.repair.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class OrdenReparacionRequest {
    @NotNull(message = "El cliente es obligatorio")
    private Long clienteId;

    @NotNull(message = "El dispositivo es obligatorio")
    private Long dispositivoId;

    @NotBlank(message = "El problema reportado es obligatorio")
    private String problemaReportado;

    private String diagnosticoTecnico;

    @Min(value = 0, message = "El costo estimado no puede ser negativo")
    private Double costoEstimado;

    @Min(value = 0, message = "El costo final no puede ser negativo")
    private Double costoFinal;

    private LocalDate fechaEntregaEstimada;

    @Min(value = 0, message = "La garantía no puede ser negativa")
    private Integer diasGarantia;

    private String nombreFirmaCliente;
    private String textoConfirmacion;
    private String tecnicoResponsable;

    @Valid
    private List<ParteOrdenReparacionRequest> partes;
}
