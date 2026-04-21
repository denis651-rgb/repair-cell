package com.store.repair.repository;

import com.store.repair.domain.CuentaPorCobrar;
import com.store.repair.domain.EstadoCuentaPorCobrar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Long> {

    @Override
    @EntityGraph(attributePaths = { "cliente", "venta", "abonos" })
    Optional<CuentaPorCobrar> findById(Long id);

    @EntityGraph(attributePaths = { "cliente", "venta", "abonos" })
    Optional<CuentaPorCobrar> findByVentaId(Long ventaId);

    @EntityGraph(attributePaths = { "cliente", "venta", "abonos" })
    @Query("""
            select cxc from CuentaPorCobrar cxc
            left join cxc.cliente c
            left join cxc.venta v
            where (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(c.nombreCompleto, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(c.telefono, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(v.numeroComprobante, '')) like lower(concat('%', :busqueda, '%')))
              and (:estado is null or cxc.estado = :estado)
            order by cxc.fechaEmision desc, cxc.id desc
            """)
    Page<CuentaPorCobrar> search(
            @Param("busqueda") String busqueda,
            @Param("estado") EstadoCuentaPorCobrar estado,
            Pageable pageable);
}
