package com.store.repair.controller;

import com.store.repair.domain.CajaDiaria;
import com.store.repair.dto.CajaResumenActualResponse;
import com.store.repair.service.AccountingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contabilidad/caja")
@RequiredArgsConstructor
@Validated
public class CajaController {

    private final AccountingService accountingService;

    @GetMapping("/actual")
    public ResponseEntity<CajaDiaria> getActual() {
        CajaDiaria caja = accountingService.getCajaActual();
        return caja == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(caja);
    }

    @GetMapping("/resumen-actual")
    public ResponseEntity<CajaResumenActualResponse> getResumenActual() {
        return ResponseEntity.ok(accountingService.getCajaResumenActual());
    }

    @PostMapping("/abrir")
    public ResponseEntity<CajaDiaria> abrir(@Valid @RequestBody AbrirCajaRequest request) {
        return ResponseEntity.ok(accountingService.abrirCaja(request.getMontoApertura(), request.getUsuario()));
    }

    @PostMapping("/cerrar")
    public ResponseEntity<CajaDiaria> cerrar(@Valid @RequestBody CerrarCajaRequest request) {
        return ResponseEntity.ok(accountingService.cerrarCaja(
                request.getId(),
                request.getMontoCierre(),
                request.getUsuario(),
                request.getObservaciones()));
    }

    @Data
    public static class AbrirCajaRequest {
        @NotNull(message = "El monto de apertura es obligatorio")
        @Min(value = 0, message = "El monto de apertura no puede ser negativo")
        private Double montoApertura;

        @NotBlank(message = "El usuario es obligatorio")
        private String usuario;
    }

    @Data
    public static class CerrarCajaRequest {
        @NotNull(message = "El id de la caja es obligatorio")
        private Long id;

        @NotNull(message = "El monto de cierre es obligatorio")
        @Min(value = 0, message = "El monto de cierre no puede ser negativo")
        private Double montoCierre;

        @NotBlank(message = "El usuario es obligatorio")
        private String usuario;

        private String observaciones;
    }
}
