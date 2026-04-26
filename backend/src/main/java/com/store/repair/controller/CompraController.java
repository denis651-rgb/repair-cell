package com.store.repair.controller;

import com.store.repair.domain.Compra;
import com.store.repair.dto.CompraRegistroRequest;
import com.store.repair.service.CompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_COMPRAS_VIEW')")
public class CompraController {

    private final CompraService service;

    @GetMapping("/paginado")
    public Page<Compra> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return service.findPage(busqueda, pagina, tamano);
    }

    @GetMapping("/{id}")
    public Compra findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Compra create(@Valid @RequestBody CompraRegistroRequest request) {
        return service.registrarCompra(request);
    }
}
