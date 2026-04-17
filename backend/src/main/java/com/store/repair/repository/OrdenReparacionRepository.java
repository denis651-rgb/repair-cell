package com.store.repair.repository;

import com.store.repair.domain.EstadoReparacion;
import com.store.repair.domain.OrdenReparacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrdenReparacionRepository extends JpaRepository<OrdenReparacion, Long> {
    long countByEstadoNot(EstadoReparacion estado);
    List<OrdenReparacion> findByRecibidoEnBetweenOrderByRecibidoEnAsc(LocalDateTime inicio, LocalDateTime fin);
    Page<OrdenReparacion> findByNumeroOrdenContainingIgnoreCaseOrClienteNombreCompletoContainingIgnoreCaseOrderByRecibidoEnDesc(
            String numeroOrden, String cliente, Pageable pageable);
}
