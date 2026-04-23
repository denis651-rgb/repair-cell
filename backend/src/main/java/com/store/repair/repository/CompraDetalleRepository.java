package com.store.repair.repository;

import com.store.repair.domain.CompraDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraDetalleRepository extends JpaRepository<CompraDetalle, Long> {

    boolean existsByProductoId(Long productoId);

    boolean existsByVarianteId(Long varianteId);
}
