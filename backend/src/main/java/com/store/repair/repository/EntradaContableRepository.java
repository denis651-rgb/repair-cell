package com.store.repair.repository;

import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.TipoEntrada;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select e from EntradaContable e
            where (:fechaInicio is null or e.fechaEntrada >= :fechaInicio)
              and (:fechaFin is null or e.fechaEntrada <= :fechaFin)
              and (:tipoEntrada is null or e.tipoEntrada = :tipoEntrada)
              and (
                   :moduloRelacionado is null
                   or trim(:moduloRelacionado) = ''
                   or (:moduloRelacionado = 'MANUAL' and e.moduloRelacionado is null)
                   or coalesce(e.moduloRelacionado, '') = :moduloRelacionado
              )
              and (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(e.categoria, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(e.descripcion, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(e.moduloRelacionado, '')) like lower(concat('%', :busqueda, '%')))
            order by e.fechaEntrada desc, e.id desc
            """)
    Page<EntradaContable> search(
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            @Param("busqueda") String busqueda,
            @Param("tipoEntrada") TipoEntrada tipoEntrada,
            @Param("moduloRelacionado") String moduloRelacionado,
            Pageable pageable);
}
