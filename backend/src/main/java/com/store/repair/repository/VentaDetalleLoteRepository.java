package com.store.repair.repository;

import com.store.repair.domain.VentaDetalleLote;
import com.store.repair.dto.RentabilidadMovimientoProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VentaDetalleLoteRepository extends JpaRepository<VentaDetalleLote, Long> {

    boolean existsByLoteId(Long loteId);

    @Query("""
            select
                lot.id as loteId,
                lot.codigoLote as codigoLote,
                var.id as varianteId,
                var.codigoVariante as codigoVariante,
                pb.id as productoBaseId,
                pb.codigoBase as codigoBase,
                pb.nombreBase as nombreBase,
                mar.nombre as marcaNombre,
                cat.nombre as categoriaNombre,
                pb.modelo as modelo,
                var.calidad as calidad,
                venta.fechaVenta as fechaVenta,
                vdl.cantidad as cantidad,
                vdl.cantidadDevuelta as cantidadDevuelta,
                vd.precioVentaUnitario as precioVentaUnitario,
                vdl.costoUnitarioAplicado as costoUnitarioAplicado
            from VentaDetalleLote vdl
            join vdl.ventaDetalle vd
            join vd.venta venta
            join vdl.lote lot
            join lot.variante var
            join var.productoBase pb
            left join pb.marca mar
            left join pb.categoria cat
            where (:inicio is null or venta.fechaVenta >= :inicio)
              and (:fin is null or venta.fechaVenta <= :fin)
              and (:marcaId is null or mar.id = :marcaId)
              and (:categoriaId is null or cat.id = :categoriaId)
              and (:calidad is null or trim(:calidad) = '' or lower(coalesce(var.calidad, '')) like lower(concat('%', :calidad, '%')))
            """)
    List<RentabilidadMovimientoProjection> findRentabilidadMovimientos(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin,
            @Param("marcaId") Long marcaId,
            @Param("categoriaId") Long categoriaId,
            @Param("calidad") String calidad);
}
