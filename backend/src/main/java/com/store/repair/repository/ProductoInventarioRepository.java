package com.store.repair.repository;

import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.MarcaInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoInventarioRepository extends JpaRepository<ProductoInventario, Long> {

    List<ProductoInventario> findAllByOrderByNombreAsc();

    Optional<ProductoInventario> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    List<ProductoInventario> findByCantidadStockLessThanEqualOrderByCantidadStockAsc(Integer cantidadStock);

    boolean existsByCategoriaId(Long categoriaId);

    boolean existsByMarcaId(Long marcaId);

    @Query("""
            select p from ProductoInventario p
            left join p.categoria c
            left join p.marca m
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(p.nombre, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.sku, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.descripcion, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(m.nombre, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.calidad, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%'))
            order by p.nombre asc
            """)
    Page<ProductoInventario> search(@Param("busqueda") String busqueda, Pageable pageable);

    @Query("""
            select p from ProductoInventario p
            left join p.categoria c
            left join p.marca m
            where (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(p.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(p.sku, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(p.descripcion, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(p.calidad, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%')))
              and (:categoriaId is null or c.id = :categoriaId)
              and (:marcaId is null or m.id = :marcaId)
            order by p.nombre asc
            """)
    Page<ProductoInventario> searchWithFilters(
            @Param("busqueda") String busqueda,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            Pageable pageable);

    @Query("""
            select p from ProductoInventario p
            where p.id <> coalesce(:excludeId, -1)
              and p.categoria.id = :categoriaId
              and p.marca.id = :marcaId
              and lower(coalesce(p.nombre, '')) = lower(:nombre)
              and lower(coalesce(p.calidad, '')) = lower(coalesce(:calidad, ''))
            order by p.nombre asc
            """)
    List<ProductoInventario> findFunctionalDuplicates(
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("nombre") String nombre,
            @Param("calidad") String calidad,
            @Param("excludeId") Long excludeId);
}
