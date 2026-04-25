package com.store.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestoreRemoteValidationRequest {

    @NotBlank(message = "El fileId remoto es obligatorio")
    private String fileId;
}
