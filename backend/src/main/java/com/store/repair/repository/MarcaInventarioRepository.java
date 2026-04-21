package com.store.repair.repository;

import com.store.repair.domain.MarcaInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarcaInventarioRepository extends JpaRepository<MarcaInventario, Long> {

    List<MarcaInventario> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
