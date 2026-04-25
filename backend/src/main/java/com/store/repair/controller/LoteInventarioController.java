package com.store.repair.controller;

import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import com.store.repair.dto.CerrarLoteManualRequest;
import com.store.repair.dto.CodigoSugeridoResponse;
import com.store.repair.dto.LoteInventarioHistorialResponse;
import com.store.repair.dto.LoteInventarioRequest;
import com.store.repair.service.LoteInventarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo/lotes")
@RequiredArgsConstructor
public class LoteInventarioController {

    private final LoteInventarioService service;

    @GetMapping
    public List<LoteInventario> search(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long varianteId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) EstadoLoteInventario estado,
            @RequestParam(defaultValue = "false") boolean soloOperativos) {
        return service.search(busqueda, varianteId, categoriaId, marcaId, modelo, estado, soloOperativos);
    }

    @GetMapping("/{id}")
    public LoteInventario findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/sugerir-codigo-proveedor")
    public CodigoSugeridoResponse sugerirCodigoProveedor(@RequestParam Long proveedorId) {
        return new CodigoSugeridoResponse(service.sugerirCodigoProveedor(proveedorId));
    }

    @GetMapping("/{id}/detalle")
    public LoteInventarioHistorialResponse findDetalle(@PathVariable Long id) {
        return service.findDetalleHistorialById(id);
    }

    @GetMapping("/historico")
    public Page<LoteInventarioHistorialResponse> historial(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long varianteId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String calidad,
            @RequestParam(required = false) EstadoLoteInventario estado,
            @RequestParam(defaultValue = "false") boolean soloOperativos,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return service.searchHistorial(
                busqueda,
                varianteId,
                categoriaId,
                marcaId,
                modelo,
                calidad,
                estado,
                soloOperativos,
                pagina,
                tamano);
    }

    @PostMapping
    public LoteInventario create(@Valid @RequestBody LoteInventarioRequest request) {
        return service.save(null, request);
    }

    @PutMapping("/{id}")
    public LoteInventario update(@PathVariable Long id, @Valid @RequestBody LoteInventarioRequest request) {
        return service.save(id, request);
    }

    @PostMapping("/{id}/cerrar-manual")
    public LoteInventario cerrarManual(@PathVariable Long id, @Valid @RequestBody CerrarLoteManualRequest request) {
        return service.cerrarManual(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
