package com.store.repair.repository;

import com.store.repair.domain.Compra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    @Override
    @EntityGraph(attributePaths = { "proveedor", "detalles", "detalles.producto" })
    java.util.Optional<Compra> findById(Long id);

    @EntityGraph(attributePaths = { "proveedor", "detalles", "detalles.producto" })
    @Query("""
            select c from Compra c
            left join c.proveedor p
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(c.numeroComprobante, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.nombreComercial, '')) like lower(concat('%', :busqueda, '%'))
            order by c.fechaCompra desc, c.id desc
            """)
    Page<Compra> search(@Param("busqueda") String busqueda, Pageable pageable);
}
