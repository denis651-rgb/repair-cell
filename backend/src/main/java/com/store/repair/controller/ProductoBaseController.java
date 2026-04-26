package com.store.repair.controller;

import com.store.repair.domain.ProductoBase;
import com.store.repair.dto.CodigoSugeridoResponse;
import com.store.repair.dto.ProductoBaseRequest;
import com.store.repair.service.ProductoBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo/productos-base")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_INVENTARIO_VIEW')")
public class ProductoBaseController {

    private final ProductoBaseService service;

    @GetMapping
    public List<ProductoBase> search(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) String modelo,
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return service.search(busqueda, categoriaId, marcaId, modelo, soloActivos);
    }

    @GetMapping("/{id}")
    public ProductoBase findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/sugerir-codigo")
    public CodigoSugeridoResponse sugerirCodigo(
            @RequestParam Long categoriaId,
            @RequestParam Long marcaId) {
        return new CodigoSugeridoResponse(service.sugerirCodigo(categoriaId, marcaId));
    }

    @PostMapping
    public ProductoBase create(@Valid @RequestBody ProductoBaseRequest request) {
        return service.save(null, request);
    }

    @PutMapping("/{id}")
    public ProductoBase update(@PathVariable Long id, @Valid @RequestBody ProductoBaseRequest request) {
        return service.save(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
