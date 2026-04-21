package com.store.repair.controller;

import com.store.repair.domain.MovimientoStock;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.service.MovimientoInventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/movimientos")
@RequiredArgsConstructor
public class MovimientoStockController {

    private final MovimientoInventarioService service;

    @GetMapping
    public List<MovimientoStock> findAll() {
        return service.findPage(null, null, null, null, 0, 100).getContent();
    }

    @GetMapping("/paginado")
    public Page<MovimientoStock> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) TipoMovimientoStock tipoMovimiento,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "6") int tamano) {
        return service.findPage(busqueda, categoriaId, marcaId, tipoMovimiento, pagina, tamano);
    }

    @GetMapping("/producto/{productoId}")
    public List<MovimientoStock> findByProducto(@PathVariable Long productoId) {
        return service.findByProducto(productoId);
    }
}
