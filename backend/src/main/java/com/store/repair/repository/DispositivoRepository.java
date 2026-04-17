package com.store.repair.repository;

import com.store.repair.domain.Dispositivo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    @Override
    @EntityGraph(attributePaths = "cliente")
    List<Dispositivo> findAll();

    @EntityGraph(attributePaths = "cliente")
    @Query("""
            select d from Dispositivo d
            left join d.cliente c
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(c.nombreCompleto, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(d.marca, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(d.modelo, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(d.imeiSerie, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(d.color, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(d.codigoBloqueo, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(d.accesorios, '')) like lower(concat('%', :busqueda, '%'))
            order by d.id desc
            """)
    Page<Dispositivo> search(@Param("busqueda") String busqueda, Pageable pageable);

    @EntityGraph(attributePaths = "cliente")
    List<Dispositivo> findAllByClienteId(Long clienteId);
}
