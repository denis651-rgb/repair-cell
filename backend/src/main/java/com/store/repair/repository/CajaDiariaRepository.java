package com.store.repair.repository;

import com.store.repair.domain.CajaDiaria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CajaDiariaRepository extends JpaRepository<CajaDiaria, Long> {
    Optional<CajaDiaria> findByEstado(String estado);
}
