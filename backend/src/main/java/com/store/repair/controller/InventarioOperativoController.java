package com.store.repair.controller;

import com.store.repair.dto.InventarioOperativoVarianteResponse;
import com.store.repair.service.ProductoVarianteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo/inventario-operativo")
@RequiredArgsConstructor
public class InventarioOperativoController {

    private final ProductoVarianteService productoVarianteService;

    @GetMapping
    public Page<InventarioOperativoVarianteResponse> search(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String calidad,
            @RequestParam(defaultValue = "true") boolean soloConStock,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return productoVarianteService.searchInventarioOperativo(
                busqueda,
                categoriaId,
                marcaId,
                modelo,
                calidad,
                soloConStock,
                pagina,
                tamano);
    }

    @GetMapping("/{varianteId}")
    public InventarioOperativoVarianteResponse findByVariante(@PathVariable Long varianteId) {
        return productoVarianteService.findInventarioOperativoById(varianteId);
    }
}
