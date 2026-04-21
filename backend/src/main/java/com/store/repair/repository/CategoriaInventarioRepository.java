package com.store.repair.repository;

import com.store.repair.domain.CategoriaInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaInventarioRepository extends JpaRepository<CategoriaInventario, Long> {

    List<CategoriaInventario> findAllByOrderByNombreAsc();

    Optional<CategoriaInventario> findByNombre(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
