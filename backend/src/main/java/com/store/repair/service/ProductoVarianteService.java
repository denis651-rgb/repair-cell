package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.ProductoBase;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.dto.InventarioOperativoVarianteResponse;
import com.store.repair.dto.ProductoVarianteRequest;
import com.store.repair.repository.LoteInventarioRepository;
import com.store.repair.repository.ProductoVarianteRepository;
import com.store.repair.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductoVarianteService {

    private static final int MAX_INTENTOS_GENERACION = 8;

    private final ProductoVarianteRepository repository;
    private final LoteInventarioRepository loteRepository;
    private final ProductoBaseService productoBaseService;
    private final ProveedorRepository proveedorRepository;

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

    public String sugerirCodigo(Long productoBaseId, String calidad) {
        ProductoBase productoBase = productoBaseService.findById(productoBaseId);
        String calidadNormalizada = validarTextoObligatorio(calidad, "La calidad es obligatoria");
        return generarSiguienteCodigoVariante(productoBase, calidadNormalizada);
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public ProductoVariante save(Long id, ProductoVarianteRequest request) {
        ProductoBase productoBase = productoBaseService.findById(request.getProductoBaseId());
        String calidad = validarTextoObligatorio(request.getCalidad(), "La calidad es obligatoria");
        String tipoPresentacion = SanitizadorTexto.limpiar(request.getTipoPresentacion());

        validarTipoPresentacion(tipoPresentacion);
        validarDuplicadoComercial(productoBase.getId(), id, calidad, tipoPresentacion);

        if (request.getPrecioVentaSugerido() == null || request.getPrecioVentaSugerido() < 0) {
            throw new BusinessException("El precio sugerido de la variante no puede ser negativo.");
        }
        if (request.getStockMinimo() != null && request.getStockMinimo() < 0) {
            throw new BusinessException("El stock minimo de la variante no puede ser negativo.");
        }

        if (id == null) {
            return crearVariante(request, productoBase, calidad, tipoPresentacion);
        }

        return actualizarVariante(id, request, productoBase, calidad, tipoPresentacion);
    }

    public Page<InventarioOperativoVarianteResponse> searchInventarioOperativo(
            String busqueda,
            Long categoriaId,
            Long marcaId,
            String modelo,
            String calidad,
            boolean soloStockBajo,
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
                .map(this::toInventarioOperativoResponse)
                .filter(response -> !soloConStock
                        || soloStockBajo
                        || (response.getStockDisponibleTotal() == null ? 0 : response.getStockDisponibleTotal()) > 0)
                .filter(response -> !soloStockBajo || Boolean.TRUE.equals(response.getStockBajo()))
                .sorted(Comparator
                        .comparing(
                                (InventarioOperativoVarianteResponse item) -> !Boolean.TRUE.equals(item.getStockBajo()))
                        .thenComparing(
                                item -> item.getFaltanteReposicion() == null ? 0 : item.getFaltanteReposicion(),
                                Comparator.reverseOrder())
                        .thenComparing(InventarioOperativoVarianteResponse::getMarcaNombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getModelo, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getCalidad, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getCodigoVariante, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return paginar(resultados, pagina, tamano);
    }

    public List<InventarioOperativoVarianteResponse> findInventarioOperativoStockBajo(
            String busqueda,
            Long categoriaId,
            Long marcaId,
            String modelo,
            String calidad) {
        return repository.search(
                        busqueda == null ? "" : busqueda.trim(),
                        null,
                        categoriaId,
                        marcaId,
                        modelo == null ? "" : modelo.trim(),
                        calidad == null ? "" : calidad.trim(),
                        true)
                .stream()
                .peek(this::aplicarResumenLotes)
                .map(this::toInventarioOperativoResponse)
                .filter(response -> Boolean.TRUE.equals(response.getStockBajo()))
                .sorted(Comparator
                        .comparing(
                                (InventarioOperativoVarianteResponse item) -> item.getFaltanteReposicion() == null ? 0 : item.getFaltanteReposicion(),
                                Comparator.reverseOrder())
                        .thenComparing(InventarioOperativoVarianteResponse::getMarcaNombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getModelo, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getCalidad, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(InventarioOperativoVarianteResponse::getCodigoVariante, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
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

    private ProductoVariante crearVariante(
            ProductoVarianteRequest request,
            ProductoBase productoBase,
            String calidad,
            String tipoPresentacion) {
        for (int intento = 0; intento < MAX_INTENTOS_GENERACION; intento++) {
            ProductoVariante destino = new ProductoVariante();
            aplicarCamposVariante(destino, request, productoBase, calidad, tipoPresentacion);
            destino.setCodigoVariante(generarSiguienteCodigoVariante(productoBase, calidad));

            try {
                ProductoVariante guardada = repository.saveAndFlush(destino);
                aplicarResumenLotes(guardada);
                return guardada;
            } catch (DataIntegrityViolationException exception) {
                if (esConflictoDeCodigo(exception)) {
                    continue;
                }
                if (esDuplicadoComercial(exception)) {
                    throw new BusinessException("Ya existe una variante con la misma calidad y presentacion para este producto base.");
                }
                throw exception;
            }
        }

        throw new BusinessException("No se pudo generar un codigo unico para la variante. Intenta nuevamente.");
    }

    private ProductoVariante actualizarVariante(
            Long id,
            ProductoVarianteRequest request,
            ProductoBase productoBase,
            String calidad,
            String tipoPresentacion) {
        ProductoVariante destino = findById(id);
        aplicarCamposVariante(destino, request, productoBase, calidad, tipoPresentacion);

        String codigoExistente = SanitizadorTexto.limpiar(destino.getCodigoVariante());
        if (codigoExistente == null) {
            destino.setCodigoVariante(generarSiguienteCodigoVariante(productoBase, calidad));
        } else {
            destino.setCodigoVariante(normalizarCodigo(codigoExistente, "El codigo de variante es obligatorio"));
        }

        boolean codigoDuplicado = repository.existsByCodigoVarianteIgnoreCaseAndIdNot(destino.getCodigoVariante(), id);
        if (codigoDuplicado) {
            throw new BusinessException("Ya existe una variante con el codigo " + destino.getCodigoVariante() + ".");
        }

        try {
            ProductoVariante guardada = repository.saveAndFlush(destino);
            aplicarResumenLotes(guardada);
            return guardada;
        } catch (DataIntegrityViolationException exception) {
            if (esConflictoDeCodigo(exception)) {
                throw new BusinessException("No se pudo actualizar la variante porque el codigo ya existe.");
            }
            if (esDuplicadoComercial(exception)) {
                throw new BusinessException("Ya existe una variante con la misma calidad y presentacion para este producto base.");
            }
            throw exception;
        }
    }

    private void aplicarCamposVariante(
            ProductoVariante destino,
            ProductoVarianteRequest request,
            ProductoBase productoBase,
            String calidad,
            String tipoPresentacion) {
        destino.setProductoBase(productoBase);
        destino.setCalidad(calidad);
        destino.setTipoPresentacion(tipoPresentacion);
        destino.setColor(SanitizadorTexto.limpiar(request.getColor()));
        destino.setPrecioVentaSugerido(request.getPrecioVentaSugerido());
        destino.setStockMinimo(request.getStockMinimo() == null ? 0 : request.getStockMinimo());
        destino.setActivo(request.getActivo() == null ? Boolean.TRUE : request.getActivo());
    }

    private String generarSiguienteCodigoVariante(ProductoBase productoBase, String calidad) {
        String codigoBase = validarTextoObligatorio(
                productoBase == null ? null : productoBase.getCodigoBase(),
                "El codigo base es obligatorio para generar la variante.");
        String calidadCodigo = abreviarSegmentoCodigo(calidad);
        String prefijo = normalizarCodigo(codigoBase, "El codigo base es obligatorio") + "-" + calidadCodigo;
        String ultimoCodigo = repository.findTopByCodigoVarianteStartingWithOrderByCodigoVarianteDesc(prefijo + "-")
                .map(ProductoVariante::getCodigoVariante)
                .orElse(null);
        int siguienteCorrelativo = extraerSiguienteCorrelativo(ultimoCodigo, 3);
        return prefijo + "-" + String.format("%03d", siguienteCorrelativo);
    }

    private int extraerSiguienteCorrelativo(String ultimoCodigo, int longitudEsperada) {
        if (ultimoCodigo == null || ultimoCodigo.isBlank()) {
            return 1;
        }

        int indice = ultimoCodigo.lastIndexOf('-');
        if (indice < 0 || indice == ultimoCodigo.length() - 1) {
            return 1;
        }

        String sufijo = ultimoCodigo.substring(indice + 1).trim();
        if (sufijo.length() != longitudEsperada) {
            return 1;
        }

        try {
            return Integer.parseInt(sufijo) + 1;
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private String abreviarSegmentoCodigo(String valor) {
        String limpio = SanitizadorTexto.limpiar(valor);
        if (limpio == null) {
            throw new BusinessException("No se pudo generar el codigo porque falta la calidad.");
        }

        String normalizado = java.text.Normalizer.normalize(limpio, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace(" ", "");

        if (normalizado.isBlank()) {
            throw new BusinessException("No se pudo generar el codigo porque la calidad es invalida.");
        }

        return normalizado.length() <= 3 ? normalizado : normalizado.substring(0, 3);
    }

    private void validarTipoPresentacion(String tipoPresentacion) {
        if (tipoPresentacion == null) {
            return;
        }
        if (proveedorRepository.existsByNombreComercialIgnoreCase(tipoPresentacion)) {
            throw new BusinessException(
                    "El tipo de presentacion no puede ser un proveedor. Usa valores como Pantalla completa, Con marco o Flex V3.");
        }
    }

    private void validarDuplicadoComercial(Long productoBaseId, Long varianteId, String calidad, String tipoPresentacion) {
        if (repository.existsVarianteComercialDuplicada(productoBaseId, varianteId, calidad, tipoPresentacion)) {
            throw new BusinessException("Ya existe una variante con la misma calidad y presentacion para este producto base.");
        }
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
        int stockDisponibleTotal = variante.getStockDisponibleTotal() == null ? 0 : variante.getStockDisponibleTotal();
        int stockMinimo = variante.getStockMinimo() == null ? 0 : variante.getStockMinimo();
        boolean stockBajo = stockMinimo > 0 && stockDisponibleTotal <= stockMinimo;
        int faltanteReposicion = stockBajo ? Math.max(stockMinimo - stockDisponibleTotal, 0) : 0;

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
                .stockMinimo(stockMinimo)
                .stockDisponibleTotal(stockDisponibleTotal)
                .stockBajo(stockBajo)
                .faltanteReposicion(faltanteReposicion)
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

    private boolean esConflictoDeCodigo(DataIntegrityViolationException exception) {
        String mensaje = exception.getMostSpecificCause() == null
                ? exception.getMessage()
                : exception.getMostSpecificCause().getMessage();
        if (mensaje == null) {
            return false;
        }
        String mensajeNormalizado = mensaje.toLowerCase(Locale.ROOT);
        return mensajeNormalizado.contains("codigo_variante")
                || mensajeNormalizado.contains("productos_variantes.codigo_variante");
    }

    private boolean esDuplicadoComercial(DataIntegrityViolationException exception) {
        String mensaje = exception.getMostSpecificCause() == null
                ? exception.getMessage()
                : exception.getMostSpecificCause().getMessage();
        if (mensaje == null) {
            return false;
        }
        return mensaje.toLowerCase(Locale.ROOT).contains("ux_productos_variantes_base_calidad_presentacion");
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
                    .proveedorId(lote.getProveedor() == null ? null : lote.getProveedor().getId())
                    .proveedorNombre(lote.getProveedor() == null ? null : lote.getProveedor().getNombreComercial())
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
