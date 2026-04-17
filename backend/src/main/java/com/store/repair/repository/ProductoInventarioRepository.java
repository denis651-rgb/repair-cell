package com.store.repair.repository;

import com.store.repair.domain.ProductoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoInventarioRepository extends JpaRepository<ProductoInventario, Long> {

    List<ProductoInventario> findAllByOrderByNombreAsc();

    List<ProductoInventario> findByCantidadStockLessThanEqualOrderByCantidadStockAsc(Integer cantidadStock);

    @Query("""
            select p from ProductoInventario p
            left join p.categoria c
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(p.nombre, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.sku, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.descripcion, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%'))
            order by p.nombre asc
            """)
    Page<ProductoInventario> search(@Param("busqueda") String busqueda, Pageable pageable);
}
