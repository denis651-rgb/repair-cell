package com.store.repair.repository;

import com.store.repair.domain.EntradaContable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EntradaContableRepository extends JpaRepository<EntradaContable, Long> {

    List<EntradaContable> findAllByOrderByFechaEntradaDescIdDesc();
    Page<EntradaContable> findAllByOrderByFechaEntradaDescIdDesc(Pageable pageable);
    List<EntradaContable> findByFechaEntradaBetweenOrderByFechaEntradaDesc(LocalDate desde, LocalDate hasta);
    Page<EntradaContable> findByFechaEntradaBetweenOrderByFechaEntradaDesc(LocalDate desde, LocalDate hasta, Pageable pageable);
    List<EntradaContable> findByCajaId(Long cajaId);
    Optional<EntradaContable> findFirstByModuloRelacionadoAndRelacionadoId(String moduloRelacionado, Long relacionadoId);
}
