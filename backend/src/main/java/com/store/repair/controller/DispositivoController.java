package com.store.repair.controller;

import com.store.repair.domain.Dispositivo;
import com.store.repair.service.DispositivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dispositivos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_DISPOSITIVOS_VIEW')")
public class DispositivoController {

    private final DispositivoService service;

    @GetMapping
    public List<Dispositivo> findAll(@RequestParam(value = "clienteId", required = false) Long clienteId) {
        return clienteId != null ? service.findByCliente(clienteId) : service.findAll();
    }

    @GetMapping("/paginado")
    public Page<Dispositivo> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(busqueda, pagina, tamano);
    }

    @GetMapping("/{id}")
    public Dispositivo findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Dispositivo create(
            @RequestParam("clienteId") Long clienteId,
            @Valid @RequestBody Dispositivo dispositivo) {
        return service.save(dispositivo, clienteId);
    }

    @PutMapping("/{id}")
    public Dispositivo update(
            @PathVariable("id") Long id,
            @RequestParam("clienteId") Long clienteId,
            @Valid @RequestBody Dispositivo dispositivo) {
        dispositivo.setId(id);
        return service.save(dispositivo, clienteId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
