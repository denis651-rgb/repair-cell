package com.store.repair.controller;

import com.store.repair.domain.Venta;
import com.store.repair.dto.DevolucionVentaRequest;
import com.store.repair.dto.VentaListadoResponse;
import com.store.repair.dto.VentaRegistroRequest;
import com.store.repair.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_VENTAS_VIEW')")
public class VentaController {

    private final VentaService service;

    @GetMapping("/paginado")
    public Page<VentaListadoResponse> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(busqueda, pagina, tamano);
    }

    @GetMapping("/{id}")
    public Venta findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Venta create(@Valid @RequestBody VentaRegistroRequest request) {
        return service.registrarVenta(request);
    }

    @PostMapping("/{id}/devolucion")
    public Venta devolucion(@PathVariable Long id, @RequestBody DevolucionVentaRequest request) {
        return service.devolverVenta(id, request);
    }
}
