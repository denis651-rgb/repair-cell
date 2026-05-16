package com.store.repair.repository;

import com.store.repair.domain.DevolucionVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta, Long> {

    List<DevolucionVenta> findByVentaIdOrderByFechaDevolucionDescIdDesc(Long ventaId);
}