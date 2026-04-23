package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.Compra;
import com.store.repair.domain.CompraDetalle;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.TipoPagoCompra;
import com.store.repair.dto.CompraDetalleRegistroRequest;
import com.store.repair.dto.CompraRegistroRequest;
import com.store.repair.dto.LoteInventarioRequest;
import com.store.repair.repository.CompraRepository;
import com.store.repair.repository.EntradaContableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository repositorio;
    private final ProveedorService proveedorService;
    private final ProductoVarianteService productoVarianteService;
    private final LoteInventarioService loteInventarioService;
    private final AccountingService accountingService;
    private final EntradaContableRepository entradaContableRepository;
    private final ComprobanteService comprobanteService;

    public Page<Compra> findPage(String busqueda, int pagina, int tamano) {
        return repositorio.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public Compra findById(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada: " + id));
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public Compra registrarCompra(CompraRegistroRequest solicitud) {
        List<CompraDetalleRegistroRequest> detallesNormalizados = normalizarDetallesCompra(solicitud.getDetalles());
        if (detallesNormalizados.isEmpty()) {
            throw new BusinessException("La compra debe incluir al menos una variante");
        }

        Compra compra = Compra.builder()
                .proveedor(proveedorService.findById(solicitud.getProveedorId()))
                .fechaCompra(solicitud.getFechaCompra() == null ? LocalDate.now() : solicitud.getFechaCompra())
                .numeroComprobante(obtenerNumeroComprobante(solicitud.getNumeroComprobante()))
                .observaciones(SanitizadorTexto.limpiar(solicitud.getObservaciones()))
                .tipoPago(solicitud.getTipoPago())
                .activa(Boolean.TRUE)
                .detalles(new ArrayList<>())
                .build();

        Compra compraGuardada = repositorio.save(compra);

        double totalCompra = 0D;
        List<CompraDetalle> detallesGuardados = new ArrayList<>();
        int indiceLote = 1;

        for (CompraDetalleRegistroRequest detalleSolicitud : detallesNormalizados) {
            ProductoVariante variante = productoVarianteService.findById(detalleSolicitud.getVarianteId());
            var productoBase = variante.getProductoBase();
            double precioVentaSugerido = detalleSolicitud.getPrecioVentaUnitario() != null
                    ? detalleSolicitud.getPrecioVentaUnitario()
                    : (variante.getPrecioVentaSugerido() == null ? 0D : variante.getPrecioVentaSugerido());
            double subtotal = detalleSolicitud.getCantidad() * detalleSolicitud.getPrecioCompraUnitario();

            LoteInventario lote = loteInventarioService.save(null, construirLoteRequest(
                    compraGuardada,
                    variante,
                    detalleSolicitud,
                    subtotal,
                    indiceLote));

            CompraDetalle detalle = CompraDetalle.builder()
                    .compra(compraGuardada)
                    .producto(null)
                    .variante(productoVarianteService.findById(variante.getId()))
                    .categoriaNombre(productoBase.getCategoria().getNombre())
                    .sku(variante.getCodigoVariante())
                    .nombreProducto(productoBase.getNombreBase())
                    .productoBaseCodigo(productoBase.getCodigoBase())
                    .marca(productoBase.getMarca().getNombre())
                    .calidad(variante.getCalidad())
                    .tipoPresentacion(variante.getTipoPresentacion())
                    .color(variante.getColor())
                    .codigoLote(lote.getCodigoLote())
                    .cantidad(detalleSolicitud.getCantidad())
                    .precioCompraUnitario(detalleSolicitud.getPrecioCompraUnitario())
                    .precioVentaUnitario(precioVentaSugerido)
                    .subtotal(subtotal)
                    .build();

            detallesGuardados.add(detalle);
            totalCompra += subtotal;
            indiceLote++;
        }

        compraGuardada.replaceDetalles(detallesGuardados);
        compraGuardada.setTotal(totalCompra);
        Compra compraFinal = repositorio.save(compraGuardada);

        if (compraFinal.getTipoPago() == TipoPagoCompra.CONTADO) {
            registrarSalidaContable(compraFinal);
        }

        return findById(compraFinal.getId());
    }

    private List<CompraDetalleRegistroRequest> normalizarDetallesCompra(List<CompraDetalleRegistroRequest> detallesOriginales) {
        if (detallesOriginales == null || detallesOriginales.isEmpty()) {
            return List.of();
        }

        Map<Long, CompraDetalleRegistroRequest> detallesConsolidados = new LinkedHashMap<>();

        for (CompraDetalleRegistroRequest detalle : detallesOriginales) {
            validarDetalleCompra(detalle);
            CompraDetalleRegistroRequest existente = detallesConsolidados.get(detalle.getVarianteId());

            if (existente == null) {
                detallesConsolidados.put(detalle.getVarianteId(), clonarDetalle(detalle));
                continue;
            }

            existente.setCantidad(existente.getCantidad() + detalle.getCantidad());
            if (detalle.getPrecioCompraUnitario() != null) {
                existente.setPrecioCompraUnitario(detalle.getPrecioCompraUnitario());
            }
            if (detalle.getPrecioVentaUnitario() != null) {
                existente.setPrecioVentaUnitario(detalle.getPrecioVentaUnitario());
            }
        }

        return new ArrayList<>(detallesConsolidados.values());
    }

    private void validarDetalleCompra(CompraDetalleRegistroRequest detalle) {
        if (detalle == null) {
            throw new BusinessException("Se recibio un detalle de compra vacio");
        }

        if (detalle.getVarianteId() == null) {
            throw new BusinessException("Cada linea de compra debe indicar una variante");
        }

        if (detalle.getCantidad() == null || detalle.getCantidad() <= 0) {
            throw new BusinessException("Cada linea de compra debe tener una cantidad mayor a cero");
        }

        if (detalle.getPrecioCompraUnitario() == null || detalle.getPrecioCompraUnitario() < 0) {
            throw new BusinessException("Cada linea de compra debe tener un costo unitario valido");
        }
    }

    private CompraDetalleRegistroRequest clonarDetalle(CompraDetalleRegistroRequest origen) {
        CompraDetalleRegistroRequest copia = new CompraDetalleRegistroRequest();
        copia.setProductoBaseId(origen.getProductoBaseId());
        copia.setVarianteId(origen.getVarianteId());
        copia.setProductoId(origen.getProductoId());
        copia.setCategoriaId(origen.getCategoriaId());
        copia.setMarcaId(origen.getMarcaId());
        copia.setSku(SanitizadorTexto.limpiar(origen.getSku()));
        copia.setNombreProducto(SanitizadorTexto.limpiar(origen.getNombreProducto()));
        copia.setCalidad(SanitizadorTexto.limpiar(origen.getCalidad()));
        copia.setCantidad(origen.getCantidad());
        copia.setPrecioCompraUnitario(origen.getPrecioCompraUnitario());
        copia.setPrecioVentaUnitario(origen.getPrecioVentaUnitario());
        return copia;
    }

    private LoteInventarioRequest construirLoteRequest(
            Compra compra,
            ProductoVariante variante,
            CompraDetalleRegistroRequest detalleSolicitud,
            double subtotal,
            int indiceLote) {
        LoteInventarioRequest request = new LoteInventarioRequest();
        request.setVarianteId(variante.getId());
        request.setCodigoLote(generarCodigoLote(compra, variante, indiceLote));
        request.setCodigoProveedor(SanitizadorTexto.limpiar(compra.getProveedor().getNombreComercial()));
        request.setFechaIngreso(compra.getFechaCompra());
        request.setCantidadInicial(detalleSolicitud.getCantidad());
        request.setCantidadDisponible(detalleSolicitud.getCantidad());
        request.setCostoUnitario(detalleSolicitud.getPrecioCompraUnitario());
        request.setSubtotalCompra(subtotal);
        request.setCompraId(compra.getId());
        request.setActivo(Boolean.TRUE);
        request.setVisibleEnVentas(Boolean.TRUE);
        return request;
    }

    private String generarCodigoLote(Compra compra, ProductoVariante variante, int indiceLote) {
        String referenciaCompra = compra.getNumeroComprobante() != null
                ? compra.getNumeroComprobante().replaceAll("[^A-Za-z0-9]+", "")
                : String.valueOf(compra.getId());
        return ("LOT-" + variante.getCodigoVariante() + "-" + referenciaCompra + "-" + indiceLote).toUpperCase();
    }

    private void registrarSalidaContable(Compra compra) {
        EntradaContable entrada = entradaContableRepository
                .findFirstByModuloRelacionadoAndRelacionadoId("COMPRA", compra.getId())
                .orElseGet(() -> EntradaContable.builder()
                        .moduloRelacionado("COMPRA")
                        .relacionadoId(compra.getId())
                        .tipoEntrada(TipoEntrada.SALIDA)
                        .build());

        entrada.setCategoria("COMPRA_INVENTARIO");
        entrada.setDescripcion("Compra " + referenciaCompra(compra) + " a " + compra.getProveedor().getNombreComercial());
        entrada.setMonto(compra.getTotal());
        entrada.setFechaEntrada(compra.getFechaCompra());

        accountingService.save(entrada);
    }

    private String referenciaCompra(Compra compra) {
        return compra.getNumeroComprobante() != null ? compra.getNumeroComprobante() : ("#" + compra.getId());
    }

    private String obtenerNumeroComprobante(String numeroRecibido) {
        String numeroNormalizado = SanitizadorTexto.limpiar(numeroRecibido);
        return numeroNormalizado != null ? numeroNormalizado : comprobanteService.generarNumeroComprobante();
    }
}
