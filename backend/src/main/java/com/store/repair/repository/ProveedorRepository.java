package com.store.repair.repository;

import com.store.repair.domain.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findAllByOrderByNombreComercialAsc();

    boolean existsByNombreComercialIgnoreCase(String nombreComercial);

    @Query("""
            select p from Proveedor p
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(p.nombreComercial, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.razonSocial, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.telefono, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(p.ciudad, '')) like lower(concat('%', :busqueda, '%'))
            order by p.nombreComercial asc
            """)
    Page<Proveedor> search(@Param("busqueda") String busqueda, Pageable pageable);
}
