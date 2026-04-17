package com.store.repair.controller;

import com.store.repair.domain.MovimientoStock;
import com.store.repair.repository.MovimientoStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/movimientos")
@RequiredArgsConstructor
public class MovimientoStockController {

    private final MovimientoStockRepository repository;

    @GetMapping
    public List<MovimientoStock> findAll() {
        return repository.findAll();
    }

    @GetMapping("/paginado")
    public Page<MovimientoStock> findPage(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "6") int tamano) {
        return repository.findAllByOrderByFechaMovimientoDescIdDesc(
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    @GetMapping("/producto/{productoId}")
    public List<MovimientoStock> findByProducto(@PathVariable Long productoId) {
        return repository.findAllByProductoId(productoId);
    }
}
