package com.store.repair.repository;

import com.store.repair.domain.MovimientoStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    @Override
    @EntityGraph(attributePaths = "producto")
    List<MovimientoStock> findAll();

    @EntityGraph(attributePaths = "producto")
    Page<MovimientoStock> findAllByOrderByFechaMovimientoDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "producto")
    List<MovimientoStock> findAllByProductoId(Long productoId);

    boolean existsByProductoId(Long productoId);

    @EntityGraph(attributePaths = { "producto", "producto.categoria", "producto.marca" })
    @Query("""
            select m from MovimientoStock m
            left join m.producto p
            left join p.categoria c
            left join p.marca ma
            where (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(p.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(p.sku, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.descripcion, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.tipoReferencia, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(ma.nombre, '')) like lower(concat('%', :busqueda, '%')))
              and (:categoriaId is null or c.id = :categoriaId)
              and (:marcaId is null or ma.id = :marcaId)
              and (:tipoMovimiento is null or m.tipoMovimiento = :tipoMovimiento)
            order by m.fechaMovimiento desc, m.id desc
            """)
    Page<MovimientoStock> search(
            @Param("busqueda") String busqueda,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("tipoMovimiento") com.store.repair.domain.TipoMovimientoStock tipoMovimiento,
            Pageable pageable);
}
