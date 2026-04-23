package com.store.repair.repository;

import com.store.repair.domain.ProductoBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoBaseRepository extends JpaRepository<ProductoBase, Long> {

    boolean existsByCodigoBaseIgnoreCase(String codigoBase);

    boolean existsByCodigoBaseIgnoreCaseAndIdNot(String codigoBase, Long id);

    boolean existsByCategoriaId(Long categoriaId);

    boolean existsByMarcaId(Long marcaId);

    @Query("""
            select pb from ProductoBase pb
            left join pb.categoria c
            left join pb.marca m
            where (:soloActivos = false or pb.activo = true)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:marcaId is null or m.id = :marcaId)
              and (:modelo is null or trim(:modelo) = '' or lower(coalesce(pb.modelo, '')) like lower(concat('%', :modelo, '%')))
              and (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(pb.codigoBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.nombreBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.modelo, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.nombre, '')) like lower(concat('%', :busqueda, '%')))
            order by pb.nombreBase asc, pb.modelo asc, pb.id asc
            """)
    List<ProductoBase> search(
            @Param("busqueda") String busqueda,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("modelo") String modelo,
            @Param("soloActivos") boolean soloActivos);
}
