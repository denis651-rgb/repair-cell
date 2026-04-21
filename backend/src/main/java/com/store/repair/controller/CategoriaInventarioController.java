package com.store.repair.controller;

import com.store.repair.domain.CategoriaInventario;
import com.store.repair.service.CategoriaInventarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/categorias")
@RequiredArgsConstructor
public class CategoriaInventarioController {

    private final CategoriaInventarioService service;

    @GetMapping
    public List<CategoriaInventario> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CategoriaInventario findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public CategoriaInventario create(@Valid @RequestBody CategoriaInventario categoria) {
        return service.save(categoria);
    }

    @PutMapping("/{id}")
    public CategoriaInventario update(@PathVariable Long id, @Valid @RequestBody CategoriaInventario categoria) {
        categoria.setId(id);
        return service.save(categoria);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
