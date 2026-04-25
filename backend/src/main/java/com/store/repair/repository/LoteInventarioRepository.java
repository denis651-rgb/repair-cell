package com.store.repair.repository;

import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoteInventarioRepository extends JpaRepository<LoteInventario, Long> {

    boolean existsByCodigoLoteIgnoreCase(String codigoLote);

    boolean existsByCodigoLoteIgnoreCaseAndIdNot(String codigoLote, Long id);

    boolean existsByVarianteId(Long varianteId);

    Optional<LoteInventario> findTopByCodigoProveedorStartingWithOrderByCodigoProveedorDesc(String prefijoCodigo);

    @Query("""
            select li from LoteInventario li
            join li.variante v
            join v.productoBase pb
            left join li.proveedor p
            left join pb.categoria c
            left join pb.marca m
            where (:varianteId is null or v.id = :varianteId)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:marcaId is null or m.id = :marcaId)
              and (:modelo is null or trim(:modelo) = '' or lower(coalesce(pb.modelo, '')) like lower(concat('%', :modelo, '%')))
              and (:estado is null or li.estado = :estado)
              and (:soloOperativos = false or (li.estado = com.store.repair.domain.EstadoLoteInventario.ACTIVO and li.visibleEnVentas = true and li.activo = true and coalesce(li.cantidadDisponible, 0) > 0))
              and (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(li.codigoLote, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(li.codigoProveedor, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(v.codigoVariante, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.codigoBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.nombreBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.modelo, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(v.calidad, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(p.nombreComercial, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%')))
            order by li.fechaIngreso desc, li.id desc
            """)
    List<LoteInventario> search(
            @Param("busqueda") String busqueda,
            @Param("varianteId") Long varianteId,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("modelo") String modelo,
            @Param("estado") EstadoLoteInventario estado,
            @Param("soloOperativos") boolean soloOperativos);

    @Query("""
            select coalesce(sum(coalesce(li.cantidadDisponible, 0)), 0)
            from LoteInventario li
            where li.variante.id = :varianteId
              and li.activo = true
              and li.estado = com.store.repair.domain.EstadoLoteInventario.ACTIVO
              and coalesce(li.cantidadDisponible, 0) > 0
            """)
    Integer sumStockDisponibleActivoByVarianteId(@Param("varianteId") Long varianteId);

    @Query("""
            select coalesce(sum(coalesce(li.cantidadDisponible, 0)), 0)
            from LoteInventario li
            where li.variante.id = :varianteId
              and li.proveedor.id = :proveedorId
              and li.activo = true
              and li.estado = com.store.repair.domain.EstadoLoteInventario.ACTIVO
              and coalesce(li.cantidadDisponible, 0) > 0
            """)
    Integer sumStockDisponibleActivoByVarianteIdAndProveedorId(
            @Param("varianteId") Long varianteId,
            @Param("proveedorId") Long proveedorId);

    @Query("""
            select count(li)
            from LoteInventario li
            where li.variante.id = :varianteId
              and li.activo = true
              and li.estado = com.store.repair.domain.EstadoLoteInventario.ACTIVO
              and coalesce(li.cantidadDisponible, 0) > 0
            """)
    Long countLotesActivosByVarianteId(@Param("varianteId") Long varianteId);

    @Query("""
            select li from LoteInventario li
            where li.variante.id = :varianteId
              and li.activo = true
              and li.visibleEnVentas = true
              and li.estado = com.store.repair.domain.EstadoLoteInventario.ACTIVO
              and coalesce(li.cantidadDisponible, 0) > 0
            order by li.fechaIngreso asc, li.id asc
            """)
    List<LoteInventario> findConsumiblesFifoByVarianteId(@Param("varianteId") Long varianteId);
}
