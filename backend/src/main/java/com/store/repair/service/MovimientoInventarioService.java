package com.store.repair.service;

import com.store.repair.domain.MovimientoStock;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.repository.MovimientoStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoInventarioService {

    private final MovimientoStockRepository repository;

    public Page<MovimientoStock> findPage(
            String busqueda,
            Long categoriaId,
            Long marcaId,
            TipoMovimientoStock tipoMovimiento,
            int pagina,
            int tamano) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                categoriaId,
                marcaId,
                tipoMovimiento,
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public List<MovimientoStock> findByProducto(Long productoId) {
        return repository.findAllByProductoId(productoId);
    }
}
