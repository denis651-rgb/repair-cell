package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketWhatsappResponse {

    private final String numeroOrden;
    private final String telefono;
    private final String telefonoNormalizado;
    private final String mensaje;
}
