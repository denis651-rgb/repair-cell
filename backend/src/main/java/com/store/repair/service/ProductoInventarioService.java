package com.store.repair.service;

import com.store.repair.domain.MovimientoStock;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.TipoMovimientoStock;
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
    private final MovimientoStockRepository movimientoStockRepository;

    public List<ProductoInventario> findAll() {
        return repository.findAllByOrderByNombreAsc();
    }

    public Page<ProductoInventario> findPage(String busqueda, int pagina, int tamano) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public List<ProductoInventario> findLowStock() {
        return repository.findByCantidadStockLessThanEqualOrderByCantidadStockAsc(Integer.MAX_VALUE).stream()
                .filter(producto -> producto.getCantidadStock() != null)
                .filter(producto -> producto.getStockMinimo() != null)
                .filter(producto -> producto.getCantidadStock() <= producto.getStockMinimo())
                .toList();
    }

    public ProductoInventario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    @Transactional
    @CacheEvict(value = { "reportes_resumen", "reportes_panel" }, allEntries = true)
    public ProductoInventario save(ProductoInventario producto, Long categoriaId) {
        producto.setCategoria(categoriaService.findById(categoriaId));

        if (producto.getActivo() == null) {
            producto.setActivo(Boolean.TRUE);
        }
        if (producto.getCantidadStock() == null) {
            producto.setCantidadStock(0);
        }
        if (producto.getStockMinimo() == null) {
            producto.setStockMinimo(0);
        }
        if (producto.getCostoUnitario() == null) {
            producto.setCostoUnitario(0D);
        }
        if (producto.getPrecioVenta() == null) {
            producto.setPrecioVenta(0D);
        }
        if (producto.getCostoPromedio() == null) {
            producto.setCostoPromedio(producto.getCostoUnitario());
        }

        if (producto.getCantidadStock() < 0 || producto.getStockMinimo() < 0) {
            throw new IllegalArgumentException("Los valores de stock no pueden ser negativos");
        }

        return repository.save(producto);
    }

    @Transactional
    @CacheEvict(value = { "reportes_resumen", "reportes_panel" }, allEntries = true)
    public ProductoInventario adjustStock(
            Long productoId,
            Integer cantidad,
            TipoMovimientoStock tipoMovimiento,
            String descripcion,
            String tipoReferencia,
            Long referenciaId,
            Double costoUnitario) {

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
        ProductoInventario guardado = repository.save(producto);

        MovimientoStock movimiento = MovimientoStock.builder()
                .producto(guardado)
                .tipoMovimiento(tipoMovimiento)
                .cantidad(cantidad)
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
        return adjustStock(productoId, cantidad, tipoMovimiento, descripcion, tipoReferencia, referenciaId, null);
    }

    @CacheEvict(value = { "reportes_resumen", "reportes_panel" }, allEntries = true)
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
