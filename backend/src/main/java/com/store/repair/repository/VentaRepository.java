package com.store.repair.repository;

import com.store.repair.domain.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    @Override
    @EntityGraph(attributePaths = { "cliente", "detalles", "detalles.producto" })
    java.util.Optional<Venta> findById(Long id);

    @EntityGraph(attributePaths = { "cliente", "detalles", "detalles.producto" })
    @Query("""
            select v from Venta v
            left join v.cliente c
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(v.numeroComprobante, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.nombreCompleto, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.telefono, '')) like lower(concat('%', :busqueda, '%'))
            order by v.fechaVenta desc, v.id desc
            """)
    Page<Venta> search(@Param("busqueda") String busqueda, Pageable pageable);
}
