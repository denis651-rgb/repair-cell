package com.store.repair.repository;

import com.store.repair.domain.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("""
            select c from Cliente c
            where :busqueda is null
               or trim(:busqueda) = ''
               or lower(coalesce(c.nombreCompleto, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.telefono, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.email, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.direccion, '')) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(c.notas, '')) like lower(concat('%', :busqueda, '%'))
            order by c.nombreCompleto asc
            """)
    Page<Cliente> search(@Param("busqueda") String busqueda, Pageable pageable);
}
