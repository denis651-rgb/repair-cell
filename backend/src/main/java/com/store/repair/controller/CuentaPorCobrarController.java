package com.store.repair.controller;

import com.store.repair.domain.CuentaPorCobrar;
import com.store.repair.domain.EstadoCuentaPorCobrar;
import com.store.repair.dto.AbonoCuentaPorCobrarRequest;
import com.store.repair.service.CuentaPorCobrarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cuentas-por-cobrar")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_CUENTAS_POR_COBRAR_VIEW')")
public class CuentaPorCobrarController {

    private final CuentaPorCobrarService service;

    @GetMapping("/paginado")
    public Page<CuentaPorCobrar> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) EstadoCuentaPorCobrar estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(busqueda, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    public CuentaPorCobrar findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/abonos")
    public CuentaPorCobrar abonar(@PathVariable Long id, @Valid @RequestBody AbonoCuentaPorCobrarRequest request) {
        return service.registrarAbono(id, request);
    }
}
