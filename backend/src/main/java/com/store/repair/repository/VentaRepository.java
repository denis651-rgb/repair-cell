package com.store.repair.repository;

import com.store.repair.domain.Venta;
import com.store.repair.dto.VentaListadoResponse;
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

    @Query(
            value = """
            select new com.store.repair.dto.VentaListadoResponse(
                v.id,
                v.fechaVenta,
                v.numeroComprobante,
                c.nombreCompleto,
                v.tipoPago,
                v.estado,
                v.total
            )
            from Venta v
            left join v.cliente c
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(v.numeroComprobante, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.nombreCompleto, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.telefono, '')) like lower(concat('%', :busqueda, '%'))
            order by v.fechaVenta desc, v.id desc
            """,
            countQuery = """
            select count(v.id) from Venta v
            left join v.cliente c
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(v.numeroComprobante, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.nombreCompleto, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.telefono, '')) like lower(concat('%', :busqueda, '%'))
            """)
    Page<VentaListadoResponse> search(@Param("busqueda") String busqueda, Pageable pageable);

    @Query("""
            select (count(v) > 0) from Venta v
            join v.detalles d
            where d.producto.id = :productoId
            """)
    boolean existsByProductoId(@Param("productoId") Long productoId);
}
