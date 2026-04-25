package com.store.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestoreExecuteRequest {

    @NotBlank(message = "La sesion de restauracion es obligatoria")
    private String sessionId;
}
