package com.store.repair.service;

import com.store.repair.domain.MovimientoStock;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.config.SanitizadorTexto;
import com.store.repair.repository.MovimientoStockRepository;
import com.store.repair.repository.ProductoInventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoInventarioService {

    private final ProductoInventarioRepository repository;
    private final CategoriaInventarioService categoriaService;
    private final MarcaInventarioService marcaService;
    private final MovimientoStockRepository movimientoStockRepository;

    public List<ProductoInventario> findAll() {
        return repository.findAllByOrderByNombreAsc();
    }

    public Page<ProductoInventario> findPage(String busqueda, int pagina, int tamano) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public Page<ProductoInventario> findPage(String busqueda, Long categoriaId, Long marcaId, int pagina, int tamano) {
        return repository.searchWithFilters(
                busqueda == null ? "" : busqueda.trim(),
                categoriaId,
                marcaId,
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public List<ProductoInventario> findLowStock() {
        return repository.findByCantidadStockLessThanEqualOrderByCantidadStockAsc(Integer.MAX_VALUE).stream()
                .filter(producto -> producto.getCantidadStock() != null)
                .filter(producto -> producto.getStockMinimo() != null)
                .filter(producto -> producto.getCantidadStock() <= producto.getStockMinimo())
                .toList();
    }

    public List<MarcaInventario> findMarcas() {
        return marcaService.findAll();
    }

    public ProductoInventario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public ProductoInventario save(ProductoInventario producto, Long categoriaId, Long marcaId) {
        ProductoInventario existente = producto.getId() == null ? null : findById(producto.getId());
        boolean esNuevo = existente == null;
        int stockInicial = producto.getCantidadStock() == null ? 0 : producto.getCantidadStock();

        if (stockInicial < 0 || (producto.getStockMinimo() != null && producto.getStockMinimo() < 0)) {
            throw new IllegalArgumentException("Los valores de stock no pueden ser negativos");
        }

        ProductoInventario objetivo = esNuevo ? producto : existente;

        if (!esNuevo) {
            objetivo.setCreadoEn(existente.getCreadoEn());
        }

        objetivo.setCategoria(categoriaService.findById(categoriaId));
        objetivo.setMarca(marcaService.findById(marcaId));
        objetivo.setSku(SanitizadorTexto.limpiar(producto.getSku()));
        objetivo.setNombre(SanitizadorTexto.limpiar(producto.getNombre()));
        objetivo.setDescripcion(SanitizadorTexto.limpiar(producto.getDescripcion()));
        objetivo.setCalidad(SanitizadorTexto.limpiar(producto.getCalidad()));
        objetivo.setActivo(producto.getActivo() == null ? Boolean.TRUE : producto.getActivo());
        objetivo.setStockMinimo(producto.getStockMinimo() == null ? 0 : producto.getStockMinimo());
        objetivo.setCostoUnitario(producto.getCostoUnitario() == null ? 0D : producto.getCostoUnitario());
        objetivo.setPrecioVenta(producto.getPrecioVenta() == null ? 0D : producto.getPrecioVenta());

        if (esNuevo) {
            objetivo.setCantidadStock(0);
            objetivo.setCostoPromedio(
                    producto.getCostoPromedio() != null
                            ? producto.getCostoPromedio()
                            : objetivo.getCostoUnitario());
        } else {
            objetivo.setCantidadStock(existente.getCantidadStock() == null ? 0 : existente.getCantidadStock());
            objetivo.setCostoPromedio(
                    existente.getCostoPromedio() != null
                            ? existente.getCostoPromedio()
                            : objetivo.getCostoUnitario());
        }

        ProductoInventario guardado = repository.save(objetivo);

        if (esNuevo && stockInicial > 0) {
            return adjustStock(
                    guardado.getId(),
                    stockInicial,
                    TipoMovimientoStock.ENTRADA,
                    "Stock inicial del producto",
                    "PRODUCTO_INICIAL",
                    guardado.getId(),
                    guardado.getCostoUnitario(),
                    guardado.getPrecioVenta());
        }

        return guardado;
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public ProductoInventario save(ProductoInventario producto, Long categoriaId) {
        Long marcaId = producto.getMarca() == null ? null : producto.getMarca().getId();
        if (marcaId == null) {
            throw new BusinessException("La marca es obligatoria para el producto");
        }
        return save(producto, categoriaId, marcaId);
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public ProductoInventario adjustStock(
            Long productoId,
            Integer cantidad,
            TipoMovimientoStock tipoMovimiento,
            String descripcion,
            String tipoReferencia,
            Long referenciaId,
            Double costoUnitario,
            Double precioVentaUnitario) {

        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }

        if (tipoMovimiento == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio");
        }

        ProductoInventario producto = findById(productoId);

        int stockPrevio = producto.getCantidadStock() == null ? 0 : producto.getCantidadStock();
        int nuevoStock;

        switch (tipoMovimiento) {
            case ENTRADA -> {
                nuevoStock = stockPrevio + cantidad;

                if (costoUnitario != null && costoUnitario > 0) {
                    double costoBase = producto.getCostoPromedio() != null
                            ? producto.getCostoPromedio()
                            : (producto.getCostoUnitario() != null ? producto.getCostoUnitario() : 0D);

                    double costoTotalActual = stockPrevio * costoBase;
                    double costoTotalNuevo = cantidad * costoUnitario;
                    double nuevoCostoPromedio = nuevoStock == 0 ? 0D
                            : (costoTotalActual + costoTotalNuevo) / nuevoStock;

                    producto.setCostoPromedio(nuevoCostoPromedio);
                    producto.setCostoUnitario(costoUnitario);
                }
            }
            case SALIDA -> {
                nuevoStock = stockPrevio - cantidad;
                if (nuevoStock < 0) {
                    throw new BusinessException("Stock insuficiente para el producto: " + producto.getNombre());
                }
            }
            case AJUSTE -> nuevoStock = cantidad;
            default -> throw new IllegalArgumentException("Tipo de movimiento no soportado");
        }

        producto.setCantidadStock(nuevoStock);
        if (precioVentaUnitario != null && precioVentaUnitario >= 0) {
            producto.setPrecioVenta(precioVentaUnitario);
        }
        ProductoInventario guardado = repository.save(producto);

        MovimientoStock movimiento = MovimientoStock.builder()
                .producto(guardado)
                .tipoMovimiento(tipoMovimiento)
                .cantidad(cantidad)
                .stockAnterior(stockPrevio)
                .stockPosterior(nuevoStock)
                .costoUnitario(costoUnitario)
                .precioVentaUnitario(precioVentaUnitario)
                .descripcion(descripcion == null ? "" : descripcion.trim())
                .tipoReferencia(tipoReferencia)
                .referenciaId(referenciaId)
                .fechaMovimiento(LocalDateTime.now())
                .build();

        movimientoStockRepository.save(movimiento);
        return guardado;
    }

    @Transactional
    public ProductoInventario adjustStock(
            Long productoId,
            Integer cantidad,
            TipoMovimientoStock tipoMovimiento,
            String descripcion,
            String tipoReferencia,
            Long referenciaId) {
        return adjustStock(productoId, cantidad, tipoMovimiento, descripcion, tipoReferencia, referenciaId, null, null);
    }

    @Transactional
    public ProductoInventario adjustStock(
            Long productoId,
            Integer cantidad,
            TipoMovimientoStock tipoMovimiento,
            String descripcion,
            String tipoReferencia,
            Long referenciaId,
            Double costoUnitario) {
        return adjustStock(productoId, cantidad, tipoMovimiento, descripcion, tipoReferencia, referenciaId, costoUnitario, null);
    }

    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
