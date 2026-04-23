package com.store.repair.controller;

import com.store.repair.domain.ProductoVariante;
import com.store.repair.dto.ProductoVarianteRequest;
import com.store.repair.service.ProductoVarianteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo/productos-variantes")
@RequiredArgsConstructor
public class ProductoVarianteController {

    private final ProductoVarianteService service;

    @GetMapping
    public List<ProductoVariante> search(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long productoBaseId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String calidad,
            @RequestParam(defaultValue = "true") boolean soloActivas) {
        return service.search(busqueda, productoBaseId, categoriaId, marcaId, modelo, calidad, soloActivas);
    }

    @GetMapping("/{id}")
    public ProductoVariante findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/por-producto-base/{productoBaseId}")
    public List<ProductoVariante> findByProductoBase(
            @PathVariable Long productoBaseId,
            @RequestParam(defaultValue = "true") boolean soloActivas) {
        return service.findByProductoBase(productoBaseId, soloActivas);
    }

    @PostMapping
    public ProductoVariante create(@Valid @RequestBody ProductoVarianteRequest request) {
        return service.save(null, request);
    }

    @PutMapping("/{id}")
    public ProductoVariante update(@PathVariable Long id, @Valid @RequestBody ProductoVarianteRequest request) {
        return service.save(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
