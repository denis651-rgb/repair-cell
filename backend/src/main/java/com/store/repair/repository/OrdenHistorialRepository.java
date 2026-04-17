package com.store.repair.repository;

import com.store.repair.domain.OrdenHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrdenHistorialRepository extends JpaRepository<OrdenHistorial, Long> {
    List<OrdenHistorial> findByOrdenIdOrderByFechaDesc(Long ordenId);
}
