package com.store.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CerrarLoteManualRequest {

    @NotBlank(message = "El motivo de cierre es obligatorio")
    private String motivo;
}
