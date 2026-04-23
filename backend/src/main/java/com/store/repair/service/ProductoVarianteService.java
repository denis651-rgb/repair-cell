package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.dto.InventarioOperativoVarianteResponse;
import com.store.repair.dto.ProductoVarianteRequest;
import com.store.repair.repository.LoteInventarioRepository;
import com.store.repair.repository.ProductoVarianteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductoVarianteService {

    private final ProductoVarianteRepository repository;
    private final LoteInventarioRepository loteRepository;
    private final ProductoBaseService productoBaseService;

    public List<ProductoVariante> search(
            String busqueda,
            Long productoBaseId,
            Long categoriaId,
            Long marcaId,
            String modelo,
            String calidad,
            boolean soloActivas) {
        List<ProductoVariante> variantes = repository.search(
                busqueda == null ? "" : busqueda.trim(),
                productoBaseId,
                categoriaId,
                marcaId,
                modelo == null ? "" : modelo.trim(),
                calidad == null ? "" : calidad.trim(),
                soloActivas);
        variantes.forEach(this::aplicarResumenLotes);
        return variantes;
    }

    public List<ProductoVariante> findByProductoBase(Long productoBaseId, boolean soloActivas) {
        productoBaseService.findById(productoBaseId);
        List<ProductoVariante> variantes = repository.findByProductoBase(productoBaseId, soloActivas);
        variantes.forEach(this::aplicarResumenLotes);
        return variantes;
    }

    public ProductoVariante findById(Long id) {
        ProductoVariante variante = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada: " + id));
        aplicarResumenLotes(variante);
        return variante;
    }

    public ProductoVariante save(Long id, ProductoVarianteRequest request) {
        ProductoVariante destino = id == null ? new ProductoVariante() : findById(id);
        String codigoNormalizado = normalizarCodigo(request.getCodigoVariante(), "El codigo de variante es obligatorio");

        boolean codigoDuplicado = id == null
                ? repository.existsByCodigoVarianteIgnoreCase(codigoNormalizado)
                : repository.existsByCodigoVarianteIgnoreCaseAndIdNot(codigoNormalizado, id);
        if (codigoDuplicado) {
            throw new BusinessException("Ya existe una variante con el codigo " + codigoNormalizado + ".");
        }

        if (request.getPrecioVentaSugerido() == null || request.getPrecioVentaSugerido() < 0) {
            throw new BusinessException("El precio sugerido de la variante no puede ser negativo.");
        }

        destino.setProductoBase(productoBaseService.findById(request.getProductoBaseId()));
        destino.setCodigoVariante(codigoNormalizado);
        destino.setCalidad(validarTextoObligatorio(request.getCalidad(), "La calidad es obligatoria"));
        destino.setTipoPresentacion(SanitizadorTexto.limpiar(request.getTipoPresentacion()));
        destino.setColor(SanitizadorTexto.limpiar(request.getColor()));
        destino.setPrecioVentaSugerido(request.getPrecioVentaSugerido());
        destino.setActivo(request.getActivo() == null ? Boolean.TRUE : request.getActivo());

        return repository.save(destino);
    }

    public Page<InventarioOperativoVarianteResponse> searchInventarioOperativo(
            String busqueda,
            Long categoriaId,
            Long marcaId,
            String modelo,
            String calidad,
            boolean soloConStock,
            int pagina,
            int tamano) {
        List<InventarioOperativoVarianteResponse> resultados = repository.search(
                        busqueda == null ? "" : busqueda.trim(),
                        null,
                        categoriaId,
                        marcaId,
                        modelo == null ? "" : modelo.trim(),
                        calidad == null ? "" : calidad.trim(),
                        true)
                .stream()
                .peek(this::aplicarResumenLotes)
                .filter(variante -> !soloConStock
                        || (variante.getStockDisponibleTotal() == null ? 0 : variante.getStockDisponibleTotal()) > 0)
                .map(this::toInventarioOperativoResponse)
                .sorted(Comparator
                        .comparing(InventarioOperativoVarianteResponse::getMarcaNombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getModelo, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getCalidad, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getCodigoVariante, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return paginar(resultados, pagina, tamano);
    }

    public InventarioOperativoVarianteResponse findInventarioOperativoById(Long varianteId) {
        ProductoVariante variante = findById(varianteId);
        return toInventarioOperativoResponse(variante);
    }

    public void delete(Long id) {
        ProductoVariante variante = findById(id);
        throw new BusinessException(
                "No se permite borrar fisicamente la variante " + variante.getCodigoVariante()
                        + ". Usa activo=false para ocultarla sin perder historial.");
    }

    private void aplicarResumenLotes(ProductoVariante variante) {
        if (variante == null || variante.getId() == null) {
            return;
        }
        Integer stock = loteRepository.sumStockDisponibleActivoByVarianteId(variante.getId());
        Long lotesActivos = loteRepository.countLotesActivosByVarianteId(variante.getId());
        variante.setStockDisponibleTotal(stock == null ? 0 : stock);
        variante.setLotesActivos(lotesActivos == null ? 0 : lotesActivos.intValue());
    }

    private InventarioOperativoVarianteResponse toInventarioOperativoResponse(ProductoVariante variante) {
        List<LoteInventario> lotesOperativos = loteRepository.search(
                "",
                variante.getId(),
                null,
                null,
                "",
                EstadoLoteInventario.ACTIVO,
                true);

        return InventarioOperativoVarianteResponse.builder()
                .varianteId(variante.getId())
                .codigoVariante(variante.getCodigoVariante())
                .productoBaseId(variante.getProductoBase() == null ? null : variante.getProductoBase().getId())
                .codigoBase(variante.getProductoBase() == null ? null : variante.getProductoBase().getCodigoBase())
                .nombreBase(variante.getProductoBase() == null ? null : variante.getProductoBase().getNombreBase())
                .categoriaNombre(variante.getProductoBase() == null || variante.getProductoBase().getCategoria() == null
                        ? null
                        : variante.getProductoBase().getCategoria().getNombre())
                .marcaNombre(variante.getProductoBase() == null || variante.getProductoBase().getMarca() == null
                        ? null
                        : variante.getProductoBase().getMarca().getNombre())
                .modelo(variante.getProductoBase() == null ? null : variante.getProductoBase().getModelo())
                .calidad(variante.getCalidad())
                .tipoPresentacion(variante.getTipoPresentacion())
                .color(variante.getColor())
                .precioVentaSugerido(variante.getPrecioVentaSugerido())
                .stockDisponibleTotal(variante.getStockDisponibleTotal() == null ? 0 : variante.getStockDisponibleTotal())
                .lotesActivos(variante.getLotesActivos() == null ? 0 : variante.getLotesActivos())
                .lotesOperativos(lotesOperativos.stream().map(lote -> LoteInventarioServiceStatic.toHistorialResponse(lote)).toList())
                .build();
    }

    private String validarTextoObligatorio(String valor, String mensaje) {
        String limpio = SanitizadorTexto.limpiar(valor);
        if (limpio == null) {
            throw new BusinessException(mensaje);
        }
        return limpio;
    }

    private String normalizarCodigo(String valor, String mensaje) {
        String limpio = validarTextoObligatorio(valor, mensaje);
        return limpio.replace(' ', '-').toUpperCase(Locale.ROOT);
    }

    private <T> Page<T> paginar(List<T> lista, int pagina, int tamano) {
        int paginaNormalizada = Math.max(pagina, 0);
        int tamanoNormalizado = tamano <= 0 ? 10 : tamano;
        int inicio = Math.min(paginaNormalizada * tamanoNormalizado, lista.size());
        int fin = Math.min(inicio + tamanoNormalizado, lista.size());
        return new PageImpl<>(
                lista.subList(inicio, fin),
                PageRequest.of(paginaNormalizada, tamanoNormalizado),
                lista.size());
    }

    private static final class LoteInventarioServiceStatic {
        private static com.store.repair.dto.LoteInventarioHistorialResponse toHistorialResponse(LoteInventario lote) {
            int cantidadInicial = lote.getCantidadInicial() == null ? 0 : lote.getCantidadInicial();
            int cantidadRestante = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
            return com.store.repair.dto.LoteInventarioHistorialResponse.builder()
                    .id(lote.getId())
                    .varianteId(lote.getVariante() == null ? null : lote.getVariante().getId())
                    .codigoVariante(lote.getVariante() == null ? null : lote.getVariante().getCodigoVariante())
                    .productoBaseId(lote.getVariante() == null || lote.getVariante().getProductoBase() == null
                            ? null
                            : lote.getVariante().getProductoBase().getId())
                    .codigoBase(lote.getVariante() == null || lote.getVariante().getProductoBase() == null
                            ? null
                            : lote.getVariante().getProductoBase().getCodigoBase())
                    .nombreBase(lote.getVariante() == null || lote.getVariante().getProductoBase() == null
                            ? null
                            : lote.getVariante().getProductoBase().getNombreBase())
                    .categoriaNombre(lote.getVariante() == null
                            || lote.getVariante().getProductoBase() == null
                            || lote.getVariante().getProductoBase().getCategoria() == null
                            ? null
                            : lote.getVariante().getProductoBase().getCategoria().getNombre())
                    .marcaNombre(lote.getVariante() == null
                            || lote.getVariante().getProductoBase() == null
                            || lote.getVariante().getProductoBase().getMarca() == null
                            ? null
                            : lote.getVariante().getProductoBase().getMarca().getNombre())
                    .modelo(lote.getVariante() == null || lote.getVariante().getProductoBase() == null
                            ? null
                            : lote.getVariante().getProductoBase().getModelo())
                    .calidad(lote.getVariante() == null ? null : lote.getVariante().getCalidad())
                    .tipoPresentacion(lote.getVariante() == null ? null : lote.getVariante().getTipoPresentacion())
                    .color(lote.getVariante() == null ? null : lote.getVariante().getColor())
                    .codigoLote(lote.getCodigoLote())
                    .codigoProveedor(lote.getCodigoProveedor())
                    .fechaIngreso(lote.getFechaIngreso())
                    .fechaCierre(lote.getFechaCierre())
                    .costoUnitario(lote.getCostoUnitario())
                    .subtotalCompra(lote.getSubtotalCompra())
                    .cantidadInicial(cantidadInicial)
                    .cantidadVendida(Math.max(cantidadInicial - cantidadRestante, 0))
                    .cantidadRestante(cantidadRestante)
                    .estado(lote.getEstado())
                    .activo(lote.getActivo())
                    .visibleEnVentas(lote.getVisibleEnVentas())
                    .compraId(lote.getCompraId())
                    .motivoCierre(lote.getMotivoCierre())
                    .build();
        }
    }
}
