package com.store.repair.controller;

import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.dto.ProductoSkuSuggestionResponse;
import com.store.repair.service.ProductoInventarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/productos")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PERM_INVENTARIO_VIEW')")
public class ProductoInventarioController {

    private final ProductoInventarioService service;

    @GetMapping
    public List<ProductoInventario> findAll() {
        return service.findAll();
    }

    @GetMapping("/paginado")
    public Page<ProductoInventario> findPage(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(busqueda, categoriaId, marcaId, pagina, tamano);
    }

    @GetMapping("/stock-bajo")
    public List<ProductoInventario> lowStock() {
        return service.findLowStock();
    }

    @GetMapping("/marcas")
    public List<MarcaInventario> marcas() {
        return service.findMarcas();
    }

    @GetMapping("/{id}")
    public ProductoInventario findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @GetMapping("/sku/sugerencia")
    public ProductoSkuSuggestionResponse suggestSku(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) String nombreModelo,
            @RequestParam(required = false) String calidad,
            @RequestParam(required = false) String skuActual,
            @RequestParam(required = false) Long productoId) {
        return service.buildSkuSuggestion(categoriaId, marcaId, nombreModelo, calidad, skuActual, productoId);
    }

    @PostMapping
    public ProductoInventario create(
            @RequestParam("categoriaId") Long categoriaId,
            @RequestParam("marcaId") Long marcaId,
            @Valid @RequestBody ProductoInventario producto) {
        return service.save(producto, categoriaId, marcaId);
    }

    @PutMapping("/{id}")
    public ProductoInventario update(
            @PathVariable("id") Long id,
            @RequestParam("categoriaId") Long categoriaId,
            @RequestParam("marcaId") Long marcaId,
            @Valid @RequestBody ProductoInventario producto) {
        producto.setId(id);
        return service.save(producto, categoriaId, marcaId);
    }

    @PostMapping("/{id}/stock")
    public ProductoInventario adjustStock(
            @PathVariable("id") Long id,
            @Valid @RequestBody AjusteStockRequest request) {
        return service.adjustStock(
                id,
                request.getCantidad(),
                request.getTipoMovimiento(),
                request.getDescripcion(),
                "MANUAL",
                id,
                request.getCostoUnitario());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    @Data
    public static class AjusteStockRequest {
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor a cero")
        private Integer cantidad;

        @NotNull(message = "El tipoMovimiento es obligatorio")
        private TipoMovimientoStock tipoMovimiento;

        private String descripcion;
        private Double costoUnitario;
    }
}
