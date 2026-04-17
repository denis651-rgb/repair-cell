package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BackupResponse {
    private final boolean ok;
    private final String mensaje;
    private final String archivo;
    private final String rutaLocal;
    private final String generadoEn;
    private final String ubicacionRemota;
    private final String estado;
    private final String origen;
}
