package com.store.repair.repository;

import com.store.repair.domain.MovimientoStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    @Override
    @EntityGraph(attributePaths = "producto")
    List<MovimientoStock> findAll();

    @EntityGraph(attributePaths = "producto")
    Page<MovimientoStock> findAllByOrderByFechaMovimientoDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "producto")
    List<MovimientoStock> findAllByProductoId(Long productoId);
}
