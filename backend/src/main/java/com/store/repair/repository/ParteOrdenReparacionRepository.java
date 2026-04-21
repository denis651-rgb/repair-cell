package com.store.repair.repository;

import com.store.repair.domain.ParteOrdenReparacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParteOrdenReparacionRepository extends JpaRepository<ParteOrdenReparacion, Long> {

    List<ParteOrdenReparacion> findAllByOrdenReparacionId(Long ordenReparacionId);

    List<ParteOrdenReparacion> findAllByProductoId(Long productoId);

    boolean existsByProductoId(Long productoId);
}
