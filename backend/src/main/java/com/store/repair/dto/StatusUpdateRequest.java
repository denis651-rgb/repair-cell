package com.store.repair.dto;

import com.store.repair.domain.EstadoReparacion;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    @NotNull(message = "El estado es obligatorio")
    private EstadoReparacion estado;
}
