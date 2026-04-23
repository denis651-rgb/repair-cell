package com.store.repair.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ComprobanteService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public String generarNumeroComprobante() {
        return LocalDateTime.now().format(FORMATO);
    }
}
