package com.store.repair.controller;

import com.store.repair.domain.OrdenReparacion;
import com.store.repair.dto.OrdenReparacionRequest;
import com.store.repair.dto.StatusUpdateRequest;
import com.store.repair.service.OrdenReparacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordenes-reparacion")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_REPARACIONES_VIEW')")
public class OrdenReparacionController {

    private final OrdenReparacionService service;
    private final com.store.repair.service.HistoryService historyService;

    @GetMapping
    public List<OrdenReparacion> findAll() {
        return service.findAll();
    }

    @GetMapping("/paginado")
    public Page<OrdenReparacion> findPage(
            @RequestParam(defaultValue = "") String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(busqueda, pagina, tamano);
    }

    @GetMapping("/{id}")
    public OrdenReparacion findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/historial")
    public List<com.store.repair.domain.OrdenHistorial> getHistory(@PathVariable Long id) {
        return historyService.getHistory(id);
    }

    @PostMapping
    public OrdenReparacion create(@Valid @RequestBody OrdenReparacionRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/estado")
    public OrdenReparacion updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.getEstado());
    }
}
