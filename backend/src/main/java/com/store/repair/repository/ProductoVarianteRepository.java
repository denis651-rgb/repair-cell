package com.store.repair.repository;

import com.store.repair.domain.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {

    boolean existsByCodigoVarianteIgnoreCase(String codigoVariante);

    boolean existsByCodigoVarianteIgnoreCaseAndIdNot(String codigoVariante, Long id);

    boolean existsByProductoBaseId(Long productoBaseId);

    @Query("""
            select pv from ProductoVariante pv
            join pv.productoBase pb
            where pb.id = :productoBaseId
              and (:soloActivas = false or pv.activo = true)
            order by pv.calidad asc, pv.tipoPresentacion asc, pv.id asc
            """)
    List<ProductoVariante> findByProductoBase(
            @Param("productoBaseId") Long productoBaseId,
            @Param("soloActivas") boolean soloActivas);

    @Query("""
            select pv from ProductoVariante pv
            join pv.productoBase pb
            left join pb.categoria c
            left join pb.marca m
            where (:soloActivas = false or pv.activo = true)
              and (:productoBaseId is null or pb.id = :productoBaseId)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:marcaId is null or m.id = :marcaId)
              and (:modelo is null or trim(:modelo) = '' or lower(coalesce(pb.modelo, '')) like lower(concat('%', :modelo, '%')))
              and (:calidad is null or trim(:calidad) = '' or lower(coalesce(pv.calidad, '')) like lower(concat('%', :calidad, '%')))
              and (:busqueda is null
                   or trim(:busqueda) = ''
                   or lower(coalesce(pv.codigoVariante, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pv.calidad, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pv.tipoPresentacion, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pv.color, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.codigoBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.nombreBase, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(pb.modelo, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(m.nombre, '')) like lower(concat('%', :busqueda, '%')))
            order by pb.nombreBase asc, pv.calidad asc, pv.tipoPresentacion asc, pv.id asc
            """)
    List<ProductoVariante> search(
            @Param("busqueda") String busqueda,
            @Param("productoBaseId") Long productoBaseId,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("modelo") String modelo,
            @Param("calidad") String calidad,
            @Param("soloActivas") boolean soloActivas);
}
