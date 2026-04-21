package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CategoriaInventario;
import com.store.repair.domain.Compra;
import com.store.repair.domain.CompraDetalle;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.domain.TipoPagoCompra;
import com.store.repair.dto.CompraDetalleRegistroRequest;
import com.store.repair.dto.CompraRegistroRequest;
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
    private final CategoriaInventarioService categoriaService;
    private final MarcaInventarioService marcaService;
    private final ProductoInventarioService productoService;
    private final AccountingService accountingService;
    private final EntradaContableRepository entradaContableRepository;

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
            throw new BusinessException("La compra debe incluir al menos un producto");
        }

        Compra compra = Compra.builder()
                .proveedor(proveedorService.findById(solicitud.getProveedorId()))
                .fechaCompra(solicitud.getFechaCompra() == null ? LocalDate.now() : solicitud.getFechaCompra())
                .numeroComprobante(SanitizadorTexto.limpiar(solicitud.getNumeroComprobante()))
                .observaciones(SanitizadorTexto.limpiar(solicitud.getObservaciones()))
                .tipoPago(solicitud.getTipoPago())
                .activa(Boolean.TRUE)
                .detalles(new ArrayList<>())
                .build();

        Compra compraGuardada = repositorio.save(compra);

        double totalCompra = 0D;
        List<CompraDetalle> detallesGuardados = new ArrayList<>();

        // Procesamos cada detalle ya consolidado para que el backend siga siendo
        // la fuente de verdad aunque el frontend cambie o reintente datos.
        for (CompraDetalleRegistroRequest detalleSolicitud : detallesNormalizados) {
            ProductoInventario producto = resolverProductoParaCompra(detalleSolicitud);
            double subtotal = detalleSolicitud.getCantidad() * detalleSolicitud.getPrecioCompraUnitario();

            productoService.adjustStock(
                    producto.getId(),
                    detalleSolicitud.getCantidad(),
                    TipoMovimientoStock.ENTRADA,
                    "Compra " + referenciaCompra(compraGuardada),
                    "COMPRA",
                    compraGuardada.getId(),
                    detalleSolicitud.getPrecioCompraUnitario(),
                    detalleSolicitud.getPrecioVentaUnitario());

            CompraDetalle detalle = CompraDetalle.builder()
                    .compra(compraGuardada)
                    .producto(productoService.findById(producto.getId()))
                    .categoriaNombre(producto.getCategoria().getNombre())
                    .sku(producto.getSku())
                    .nombreProducto(producto.getNombre())
                    .marca(producto.getMarca().getNombre())
                    .calidad(producto.getCalidad())
                    .cantidad(detalleSolicitud.getCantidad())
                    .precioCompraUnitario(detalleSolicitud.getPrecioCompraUnitario())
                    .precioVentaUnitario(detalleSolicitud.getPrecioVentaUnitario())
                    .subtotal(subtotal)
                    .build();

            detallesGuardados.add(detalle);
            totalCompra += subtotal;
        }

        compraGuardada.setDetalles(detallesGuardados);
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

        Map<String, CompraDetalleRegistroRequest> detallesConsolidados = new LinkedHashMap<>();

        for (CompraDetalleRegistroRequest detalle : detallesOriginales) {
            validarDetalleCompra(detalle);

            String clave = construirClaveDetalle(detalle);
            CompraDetalleRegistroRequest existente = detallesConsolidados.get(clave);

            if (existente == null) {
                detallesConsolidados.put(clave, clonarDetalle(detalle));
                continue;
            }

            // Consolidamos cantidades para que la compra final no cree filas
            // repetidas del mismo item aunque vengan duplicadas desde UI.
            existente.setCantidad(existente.getCantidad() + detalle.getCantidad());

            if (detalle.getPrecioCompraUnitario() != null) {
                existente.setPrecioCompraUnitario(detalle.getPrecioCompraUnitario());
            }

            if (detalle.getPrecioVentaUnitario() != null) {
                existente.setPrecioVentaUnitario(detalle.getPrecioVentaUnitario());
            }

            if (SanitizadorTexto.limpiar(detalle.getCalidad()) != null) {
                existente.setCalidad(detalle.getCalidad());
            }

            if (SanitizadorTexto.limpiar(detalle.getSku()) != null) {
                existente.setSku(detalle.getSku());
            }
        }

        return new ArrayList<>(detallesConsolidados.values());
    }

    private void validarDetalleCompra(CompraDetalleRegistroRequest detalle) {
        if (detalle == null) {
            throw new BusinessException("Se recibio un detalle de compra vacio");
        }

        if (detalle.getCantidad() == null || detalle.getCantidad() <= 0) {
            throw new BusinessException("Cada item de compra debe tener una cantidad mayor a cero");
        }

        if (detalle.getPrecioCompraUnitario() == null || detalle.getPrecioCompraUnitario() < 0) {
            throw new BusinessException("Cada item de compra debe tener un precio de compra valido");
        }

        if (detalle.getPrecioVentaUnitario() == null || detalle.getPrecioVentaUnitario() < 0) {
            throw new BusinessException("Cada item de compra debe tener un precio de venta valido");
        }

        if (detalle.getProductoId() == null) {
            if (detalle.getCategoriaId() == null) {
                throw new BusinessException("Debes seleccionar una categoria para el item nuevo");
            }

            if (detalle.getMarcaId() == null) {
                throw new BusinessException("Debes seleccionar una marca para el item nuevo");
            }

            if (SanitizadorTexto.limpiar(detalle.getNombreProducto()) == null) {
                throw new BusinessException("Debes registrar el nombre del producto para el item nuevo");
            }
        }
    }

    private CompraDetalleRegistroRequest clonarDetalle(CompraDetalleRegistroRequest origen) {
        CompraDetalleRegistroRequest copia = new CompraDetalleRegistroRequest();
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

    private String construirClaveDetalle(CompraDetalleRegistroRequest detalle) {
        if (detalle.getProductoId() != null) {
            return "producto:" + detalle.getProductoId();
        }

        return String.join("|",
                "categoria:" + (detalle.getCategoriaId() == null ? "" : detalle.getCategoriaId()),
                "marca:" + (detalle.getMarcaId() == null ? "" : detalle.getMarcaId()),
                "sku:" + valorNormalizado(detalle.getSku()),
                "nombre:" + valorNormalizado(detalle.getNombreProducto()),
                "calidad:" + valorNormalizado(detalle.getCalidad()));
    }

    private String valorNormalizado(String valor) {
        String limpio = SanitizadorTexto.limpiar(valor);
        return limpio == null ? "" : limpio.toLowerCase();
    }

    private ProductoInventario resolverProductoParaCompra(CompraDetalleRegistroRequest detalleSolicitud) {
        if (detalleSolicitud.getProductoId() != null) {
            ProductoInventario productoExistente = productoService.findById(detalleSolicitud.getProductoId());

            // La compra conserva la ficha maestra del producto existente; solo
            // permitimos refrescar el precio sugerido de venta si viene informado.
            if (detalleSolicitud.getPrecioVentaUnitario() != null && detalleSolicitud.getPrecioVentaUnitario() >= 0) {
                productoExistente.setPrecioVenta(detalleSolicitud.getPrecioVentaUnitario());
                productoExistente = productoService.save(
                        productoExistente,
                        productoExistente.getCategoria().getId(),
                        productoExistente.getMarca().getId());
            }

            return productoExistente;
        }

        ProductoInventario producto = new ProductoInventario();

        Long categoriaId = detalleSolicitud.getProductoId() == null
                ? detalleSolicitud.getCategoriaId()
                : (detalleSolicitud.getCategoriaId() != null
                        ? detalleSolicitud.getCategoriaId()
                        : producto.getCategoria().getId());

        if (categoriaId == null) {
            throw new BusinessException("La categoria es obligatoria para registrar la compra");
        }

        CategoriaInventario categoria = categoriaService.findById(categoriaId);
        Long marcaId = detalleSolicitud.getProductoId() == null
                ? detalleSolicitud.getMarcaId()
                : (detalleSolicitud.getMarcaId() != null
                        ? detalleSolicitud.getMarcaId()
                        : (producto.getMarca() != null ? producto.getMarca().getId() : null));

        if (marcaId == null) {
            throw new BusinessException("La marca es obligatoria para registrar la compra");
        }

        MarcaInventario marca = marcaService.findById(marcaId);

        producto.setCategoria(categoria);
        producto.setMarca(marca);
        producto.setSku(SanitizadorTexto.limpiar(detalleSolicitud.getSku()) != null
                ? SanitizadorTexto.limpiar(detalleSolicitud.getSku())
                : generarSkuCompra(categoria, marca, detalleSolicitud));
        producto.setNombre(SanitizadorTexto.limpiar(detalleSolicitud.getNombreProducto()));
        producto.setDescripcion(null);
        producto.setCalidad(SanitizadorTexto.limpiar(detalleSolicitud.getCalidad()));
        producto.setCostoUnitario(detalleSolicitud.getPrecioCompraUnitario());
        producto.setPrecioVenta(detalleSolicitud.getPrecioVentaUnitario());
        producto.setActivo(Boolean.TRUE);

        if (producto.getCantidadStock() == null) {
            producto.setCantidadStock(0);
        }
        if (producto.getStockMinimo() == null) {
            producto.setStockMinimo(0);
        }

        return productoService.save(producto, categoria.getId(), marca.getId());
    }

    private String generarSkuCompra(CategoriaInventario categoria, MarcaInventario marca, CompraDetalleRegistroRequest detalleSolicitud) {
        String categoriaBase = SanitizadorTexto.limpiar(categoria.getNombre());
        String marcaBase = SanitizadorTexto.limpiar(marca.getNombre());
        String nombreBase = SanitizadorTexto.limpiar(detalleSolicitud.getNombreProducto());

        String bruto = String.format("%s-%s-%s",
                categoriaBase == null ? "ITEM" : categoriaBase,
                marcaBase == null ? "GEN" : marcaBase,
                nombreBase == null ? "PRODUCTO" : nombreBase);

        String skuNormalizado = bruto
                .replaceAll("[^a-zA-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toUpperCase();

        return skuNormalizado.isBlank() ? ("ITEM-" + System.currentTimeMillis()) : skuNormalizado;
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
}
