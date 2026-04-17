package com.store.repair.repository;

import com.store.repair.domain.CategoriaInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriaInventarioRepository extends JpaRepository<CategoriaInventario, Long> {

    Optional<CategoriaInventario> findByNombre(String nombre);

    boolean existsByNombre(String nombre);
}