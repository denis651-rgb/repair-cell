package com.store.repair.repository;

import com.store.repair.domain.ProductoBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoBaseRepository extends JpaRepository<ProductoBase, Long> {

    boolean existsByCodigoBaseIgnoreCase(String codigoBase);

    boolean existsByCodigoBaseIgnoreCaseAndIdNot(String codigoBase, Long id);

    boolean existsByCategoriaId(Long categoriaId);

    boolean existsByMarcaId(Long marcaId);

    Optional<ProductoBase> findTopByCodigoBaseStartingWithOrderByCodigoBaseDesc(String prefijoCodigo);

    @Query("""
            select distinct pb from ProductoBase pb
            left join pb.categoria c
            left join pb.marca m
            where (:soloActivos = false or pb.activo = true)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:marcaId is null or m.id = :marcaId)
              and (:modelo is null
                   or trim(:modelo) = ''
                   or lower(coalesce(pb.modelo, '')) like lower(concat('%', :modelo, '%'))
                   or exists (
                        select 1
                        from ProductoBaseCompatibilidad compat
                        where compat.productoBase = pb
                          and compat.activa = true
                          and (
                              lower(coalesce(compat.modeloCompatible, '')) like lower(concat('%', :modelo, '%'))
                              or lower(coalesce(compat.codigoReferencia, '')) like lower(concat('%', :modelo, '%'))
                              or lower(coalesce(compat.marcaCompatible, '')) like lower(concat('%', :modelo, '%'))
                          )
                   ))
              and (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(pb.codigoBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.nombreBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.modelo, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or exists (
                        select 1
                        from ProductoBaseCompatibilidad compat
                        where compat.productoBase = pb
                          and compat.activa = true
                          and (
                              lower(coalesce(compat.marcaCompatible, '')) like lower(concat('%', :busqueda, '%'))
                              or lower(coalesce(compat.modeloCompatible, '')) like lower(concat('%', :busqueda, '%'))
                              or lower(coalesce(compat.codigoReferencia, '')) like lower(concat('%', :busqueda, '%'))
                              or lower(coalesce(compat.nota, '')) like lower(concat('%', :busqueda, '%'))
                          )
                   ))
            order by pb.nombreBase asc, pb.modelo asc, pb.id asc
            """)
    List<ProductoBase> search(
            @Param("busqueda") String busqueda,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("modelo") String modelo,
            @Param("soloActivos") boolean soloActivos);
}
