package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.domain.MovimientoStock;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.dto.ProductoInventarioResumenDto;
import com.store.repair.dto.ProductoSkuSuggestionResponse;
import com.store.repair.repository.CompraDetalleRepository;
import com.store.repair.repository.MovimientoStockRepository;
import com.store.repair.repository.ParteOrdenReparacionRepository;
import com.store.repair.repository.ProductoInventarioRepository;
import com.store.repair.repository.VentaRepository;
import com.store.repair.util.SkuUtils;
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
    private final CompraDetalleRepository compraDetalleRepository;
    private final VentaRepository ventaRepository;
    private final ParteOrdenReparacionRepository parteOrdenReparacionRepository;

    public List<ProductoInventario> findAll() {
        return repository.findAllByOrderByNombreAsc().stream()
                .peek(this::applyComputedFlags)
                .toList();
    }

    public Page<ProductoInventario> findPage(String busqueda, int pagina, int tamano) {
        Page<ProductoInventario> page = repository.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
        page.forEach(this::applyComputedFlags);
        return page;
    }

    public Page<ProductoInventario> findPage(String busqueda, Long categoriaId, Long marcaId, int pagina, int tamano) {
        Page<ProductoInventario> page = repository.searchWithFilters(
                busqueda == null ? "" : busqueda.trim(),
                categoriaId,
                marcaId,
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
        page.forEach(this::applyComputedFlags);
        return page;
    }

    public List<ProductoInventario> findLowStock() {
        return repository.findByCantidadStockLessThanEqualOrderByCantidadStockAsc(Integer.MAX_VALUE).stream()
                .filter(producto -> producto.getCantidadStock() != null)
                .filter(producto -> producto.getStockMinimo() != null)
                .filter(producto -> producto.getCantidadStock() <= producto.getStockMinimo())
                .peek(this::applyComputedFlags)
                .toList();
    }

    public List<MarcaInventario> findMarcas() {
        return marcaService.findAll();
    }

    public ProductoInventario findById(Long id) {
        ProductoInventario producto = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
        applyComputedFlags(producto);
        return producto;
    }

    public ProductoSkuSuggestionResponse buildSkuSuggestion(
            Long categoriaId,
            Long marcaId,
            String nombreModelo,
            String calidad,
            String skuActual,
            Long productoId) {
        String categoriaNombre = categoriaId == null ? null : categoriaService.findById(categoriaId).getNombre();
        String marcaNombre = marcaId == null ? null : marcaService.findById(marcaId).getNombre();

        return ProductoSkuSuggestionResponse.builder()
                .skuSugerido(SkuUtils.suggest(categoriaNombre, marcaNombre, nombreModelo, calidad))
                .skuNormalizado(SkuUtils.normalize(skuActual))
                .skuValido(SkuUtils.isValid(skuActual))
                .productosSimilares(
                        findFunctionalDuplicateSummaries(categoriaId, marcaId, nombreModelo, calidad, productoId))
                .build();
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
        ProductoInventario existente = producto.getId() == null ? null
                : repository.findById(producto.getId()).orElse(null);
        boolean esNuevo = existente == null;
        int stockInicial = producto.getCantidadStock() == null ? 0 : producto.getCantidadStock();

        if (stockInicial < 0 || (producto.getStockMinimo() != null && producto.getStockMinimo() < 0)) {
            throw new IllegalArgumentException("Los valores de stock no pueden ser negativos");
        }

        String skuNormalizado = normalizeAndValidateSku(producto.getSku());
        validateSkuUniqueness(skuNormalizado, producto.getId());

        if (!esNuevo && !skuNormalizado.equalsIgnoreCase(existente.getSku())
                && hasOperationalHistory(existente.getId())) {
            throw new BusinessException(
                    "El SKU no se puede editar porque el producto ya tiene compras, ventas, movimientos o uso en reparación.");
        }

        ProductoInventario objetivo = esNuevo ? producto : existente;
        if (!esNuevo) {
            objetivo.setCreadoEn(existente.getCreadoEn());
        }

        objetivo.setCategoria(categoriaService.findById(categoriaId));
        objetivo.setMarca(marcaService.findById(marcaId));
        objetivo.setSku(skuNormalizado);
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
                    producto.getCostoPromedio() != null ? producto.getCostoPromedio() : objetivo.getCostoUnitario());
        } else {
            objetivo.setCantidadStock(existente.getCantidadStock() == null ? 0 : existente.getCantidadStock());
            objetivo.setCostoPromedio(
                    existente.getCostoPromedio() != null ? existente.getCostoPromedio() : objetivo.getCostoUnitario());
        }

        ProductoInventario guardado = repository.save(objetivo);
        applyComputedFlags(guardado);

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
        applyComputedFlags(guardado);

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
        return adjustStock(productoId, cantidad, tipoMovimiento, descripcion, tipoReferencia, referenciaId,
                costoUnitario, null);
    }

    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public void delete(Long id) {
        ProductoInventario producto = findById(id);

        if (hasOperationalHistory(id)) {
            throw new BusinessException(
                    "No se puede eliminar el producto " + producto.getNombre()
                            + " porque ya tiene movimientos, compras, ventas o uso en reparaciones.");
        }

        repository.deleteById(id);
    }

    public boolean hasOperationalHistory(Long productoId) {
        if (productoId == null) {
            return false;
        }
        return movimientoStockRepository.existsByProductoId(productoId)
                || compraDetalleRepository.existsByProductoId(productoId)
                || ventaRepository.existsByProductoId(productoId)
                || parteOrdenReparacionRepository.existsByProductoId(productoId);
    }

    public List<ProductoInventarioResumenDto> findFunctionalDuplicateSummaries(
            Long categoriaId,
            Long marcaId,
            String nombre,
            String calidad,
            Long excludeId) {
        if (categoriaId == null || marcaId == null) {
            return List.of();
        }

        String nombreLimpio = SanitizadorTexto.limpiar(nombre);
        if (nombreLimpio == null) {
            return List.of();
        }

        return repository.findFunctionalDuplicates(
                categoriaId,
                marcaId,
                nombreLimpio,
                SanitizadorTexto.limpiar(calidad),
                excludeId)
                .stream()
                .map(producto -> ProductoInventarioResumenDto.builder()
                        .id(producto.getId())
                        .sku(producto.getSku())
                        .nombre(producto.getNombre())
                        .categoria(producto.getCategoria() == null ? null : producto.getCategoria().getNombre())
                        .marca(producto.getMarca() == null ? null : producto.getMarca().getNombre())
                        .calidad(producto.getCalidad())
                        .skuEditable(!hasOperationalHistory(producto.getId()))
                        .build())
                .toList();
    }

    public String ensureUniqueSkuSuggestion(String skuBase) {
        return SkuUtils.ensureUnique(skuBase, repository::existsBySkuIgnoreCase);
    }

    private String normalizeAndValidateSku(String sku) {
        String skuNormalizado = SkuUtils.normalize(sku);
        if (skuNormalizado == null) {
            throw new BusinessException("El SKU es obligatorio.");
        }
        if (!SkuUtils.isValid(skuNormalizado)) {
            throw new BusinessException(
                    "El SKU debe usar solo letras, numeros y guiones. Ejemplo valido: BAT-SAM-A04-ORI");
        }
        return skuNormalizado;
    }

    private void validateSkuUniqueness(String skuNormalizado, Long productoId) {
        boolean existe = productoId == null
                ? repository.existsBySkuIgnoreCase(skuNormalizado)
                : repository.existsBySkuIgnoreCaseAndIdNot(skuNormalizado, productoId);
        if (existe) {
            throw new BusinessException("Ya existe otro producto con el SKU " + skuNormalizado + ".");
        }
    }

    private void applyComputedFlags(ProductoInventario producto) {
        if (producto != null) {
            producto.setSkuEditable(!hasOperationalHistory(producto.getId()));
        }
    }
}
