package com.store.repair.controller;

import com.store.repair.domain.Proveedor;
import com.store.repair.service.ProveedorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService service;

    @GetMapping
    public List<Proveedor> findAll() {
        return service.findAll();
    }

    @GetMapping("/paginado")
    public Page<Proveedor> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return service.findPage(busqueda, pagina, tamano);
    }

    @GetMapping("/{id}")
    public Proveedor findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Proveedor create(@Valid @RequestBody Proveedor proveedor) {
        return service.save(proveedor);
    }

    @PutMapping("/{id}")
    public Proveedor update(@PathVariable Long id, @Valid @RequestBody Proveedor proveedor) {
        proveedor.setId(id);
        return service.save(proveedor);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
