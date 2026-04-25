package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.domain.Proveedor;
import com.store.repair.dto.CerrarLoteManualRequest;
import com.store.repair.dto.LoteInventarioHistorialResponse;
import com.store.repair.dto.LoteInventarioRequest;
import com.store.repair.repository.CompraRepository;
import com.store.repair.repository.LoteInventarioRepository;
import com.store.repair.repository.VentaDetalleLoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoteInventarioService {

    private final LoteInventarioRepository repository;
    private final ProductoVarianteService productoVarianteService;
    private final VentaDetalleLoteRepository ventaDetalleLoteRepository;
    private final ProveedorService proveedorService;
    private final CompraRepository compraRepository;

    public List<LoteInventario> search(
            String busqueda,
            Long varianteId,
            Long categoriaId,
            Long marcaId,
            String modelo,
            EstadoLoteInventario estado,
            boolean soloOperativos) {
        return repository.search(
                busqueda == null ? "" : busqueda.trim(),
                varianteId,
                categoriaId,
                marcaId,
                modelo == null ? "" : modelo.trim(),
                estado,
                soloOperativos);
    }

    public LoteInventario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado: " + id));
    }

    public LoteInventarioHistorialResponse findDetalleHistorialById(Long id) {
        return toHistorialResponse(findById(id));
    }

    public String sugerirCodigoProveedor(Long proveedorId) {
        String prefijo = proveedorService.construirPrefijoCodigoProveedor(proveedorId);
        String ultimoCodigo = repository.findTopByCodigoProveedorStartingWithOrderByCodigoProveedorDesc(prefijo)
                .map(LoteInventario::getCodigoProveedor)
                .orElse(null);
        int siguiente = extraerSiguienteCorrelativoProveedor(ultimoCodigo);
        return prefijo + String.format("%03d", siguiente);
    }

    public Page<LoteInventarioHistorialResponse> searchHistorial(
            String busqueda,
            Long varianteId,
            Long categoriaId,
            Long marcaId,
            String modelo,
            String calidad,
            EstadoLoteInventario estado,
            boolean soloOperativos,
            int pagina,
            int tamano) {
        List<LoteInventarioHistorialResponse> resultados = repository.search(
                        busqueda == null ? "" : busqueda.trim(),
                        varianteId,
                        categoriaId,
                        marcaId,
                        modelo == null ? "" : modelo.trim(),
                        estado,
                        soloOperativos)
                .stream()
                .filter(lote -> calidad == null
                        || calidad.isBlank()
                        || contieneTexto(lote.getVariante() == null ? null : lote.getVariante().getCalidad(), calidad))
                .map(this::toHistorialResponse)
                .toList();
        return paginar(resultados, pagina, tamano);
    }

    @Transactional
    public LoteInventario save(Long id, LoteInventarioRequest request) {
        LoteInventario destino = id == null ? new LoteInventario() : findById(id);
        ProductoVariante varianteDestino = productoVarianteService.findById(request.getVarianteId());
        Proveedor proveedorDestino = resolverProveedor(request, destino);
        String codigoLoteNormalizado = normalizarCodigo(request.getCodigoLote(), "El codigo de lote es obligatorio");

        boolean codigoDuplicado = id == null
                ? repository.existsByCodigoLoteIgnoreCase(codigoLoteNormalizado)
                : repository.existsByCodigoLoteIgnoreCaseAndIdNot(codigoLoteNormalizado, id);
        if (codigoDuplicado) {
            throw new BusinessException("Ya existe un lote con el codigo " + codigoLoteNormalizado + ".");
        }

        Integer cantidadInicial = request.getCantidadInicial() == null ? 0 : request.getCantidadInicial();
        Integer cantidadDisponible = request.getCantidadDisponible() == null ? 0 : request.getCantidadDisponible();
        if (cantidadDisponible > cantidadInicial) {
            throw new BusinessException("La cantidad disponible no puede ser mayor que la cantidad inicial.");
        }
        if (cantidadInicial < 0 || cantidadDisponible < 0) {
            throw new BusinessException("No se puede guardar un lote con cantidades negativas.");
        }

        Double costoUnitario = request.getCostoUnitario() == null ? 0D : request.getCostoUnitario();
        if (costoUnitario < 0) {
            throw new BusinessException("El costo unitario no puede ser negativo.");
        }

        String motivoCierre = SanitizadorTexto.limpiar(request.getMotivoCierre());
        boolean loteSeCierraPorEdicion = Boolean.FALSE.equals(request.getActivo());
        if (loteSeCierraPorEdicion && cantidadDisponible > 0 && motivoCierre == null) {
            throw new BusinessException(
                    "No puedes cerrar o inactivar un lote con stock disponible sin indicar un motivo de cierre.");
        }

        if (id != null && destino.getVariante() != null && !destino.getVariante().getId().equals(varianteDestino.getId())) {
            throw new BusinessException(
                    "No se puede cambiar la variante del lote " + destino.getCodigoLote()
                            + ". Crea un lote nuevo para la variante correcta.");
        }

        destino.setVariante(varianteDestino);
        destino.setProveedor(proveedorDestino);
        destino.setCodigoLote(codigoLoteNormalizado);
        destino.setCodigoProveedor(SanitizadorTexto.limpiar(request.getCodigoProveedor()));
        destino.setFechaIngreso(request.getFechaIngreso());
        destino.setCantidadInicial(cantidadInicial);
        destino.setCantidadDisponible(cantidadDisponible);
        destino.setCostoUnitario(costoUnitario);
        destino.setSubtotalCompra(
                request.getSubtotalCompra() != null
                        ? request.getSubtotalCompra()
                        : redondearDosDecimales(cantidadInicial * costoUnitario));
        destino.setCompraId(request.getCompraId());
        destino.setActivo(request.getActivo() == null ? Boolean.TRUE : request.getActivo());
        destino.setVisibleEnVentas(request.getVisibleEnVentas() == null ? Boolean.TRUE : request.getVisibleEnVentas());
        destino.setMotivoCierre(motivoCierre);

        aplicarEstadoAutomatico(destino);
        return repository.save(destino);
    }

    @Transactional
    public LoteInventario cerrarManual(Long id, CerrarLoteManualRequest request) {
        LoteInventario lote = findById(id);
        String motivo = SanitizadorTexto.limpiar(request == null ? null : request.getMotivo());
        int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        if (disponible > 0 && motivo == null) {
            throw new BusinessException(
                    "No puedes cerrar manualmente el lote " + lote.getCodigoLote()
                            + " con stock disponible sin indicar un motivo.");
        }
        lote.setEstado(EstadoLoteInventario.CERRADO_MANUAL);
        lote.setActivo(Boolean.FALSE);
        lote.setVisibleEnVentas(Boolean.FALSE);
        lote.setFechaCierre(LocalDateTime.now());
        lote.setMotivoCierre(motivo);
        return repository.save(lote);
    }

    @Transactional
    public void delete(Long id) {
        LoteInventario lote = findById(id);
        if (ventaDetalleLoteRepository.existsByLoteId(id)) {
            throw new BusinessException(
                    "No se puede eliminar el lote " + lote.getCodigoLote()
                            + " porque ya tiene ventas asociadas. Usa cierre manual.");
        }
        throw new BusinessException(
                "No se permite borrar fisicamente el lote " + lote.getCodigoLote()
                        + ". Usa cierre manual para conservar el historial.");
    }

    public int obtenerStockDisponiblePorVariante(Long varianteId) {
        Integer total = repository.sumStockDisponibleActivoByVarianteId(varianteId);
        return total == null ? 0 : total;
    }

    public int contarLotesActivosPorVariante(Long varianteId) {
        Long total = repository.countLotesActivosByVarianteId(varianteId);
        return total == null ? 0 : total.intValue();
    }

    public int obtenerStockDisponiblePorVarianteYProveedor(Long varianteId, Long proveedorId) {
        Integer total = repository.sumStockDisponibleActivoByVarianteIdAndProveedorId(varianteId, proveedorId);
        return total == null ? 0 : total;
    }

    public LoteInventarioHistorialResponse toHistorialResponse(LoteInventario lote) {
        if (lote == null) {
            return null;
        }

        int cantidadInicial = lote.getCantidadInicial() == null ? 0 : lote.getCantidadInicial();
        int cantidadRestante = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        int cantidadVendida = Math.max(cantidadInicial - cantidadRestante, 0);

        return LoteInventarioHistorialResponse.builder()
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
                .cantidadVendida(cantidadVendida)
                .cantidadRestante(cantidadRestante)
                .estado(lote.getEstado())
                .activo(lote.getActivo())
                .visibleEnVentas(lote.getVisibleEnVentas())
                .compraId(lote.getCompraId())
                .motivoCierre(lote.getMotivoCierre())
                .build();
    }

    private void aplicarEstadoAutomatico(LoteInventario lote) {
        if (Boolean.FALSE.equals(lote.getActivo())) {
            lote.setEstado(EstadoLoteInventario.CERRADO_MANUAL);
            lote.setVisibleEnVentas(Boolean.FALSE);
            if (lote.getFechaCierre() == null) {
                lote.setFechaCierre(LocalDateTime.now());
            }
            if (lote.getMotivoCierre() == null) {
                lote.setMotivoCierre("Cierre manual/inactivacion");
            }
            return;
        }

        int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        if (disponible <= 0) {
            lote.setCantidadDisponible(0);
            lote.setEstado(EstadoLoteInventario.AGOTADO);
            lote.setVisibleEnVentas(Boolean.FALSE);
            if (lote.getFechaCierre() == null) {
                lote.setFechaCierre(LocalDateTime.now());
            }
            lote.setMotivoCierre("Agotado por consumo");
            return;
        }

        lote.setEstado(EstadoLoteInventario.ACTIVO);
        lote.setVisibleEnVentas(Boolean.TRUE.equals(lote.getVisibleEnVentas()));
        lote.setFechaCierre(null);
        lote.setMotivoCierre(null);
    }

    private Proveedor resolverProveedor(LoteInventarioRequest request, LoteInventario destino) {
        if (request.getProveedorId() != null) {
            return proveedorService.findById(request.getProveedorId());
        }

        if (request.getCompraId() != null) {
            return compraRepository.findById(request.getCompraId())
                    .map(compra -> compra.getProveedor())
                    .orElseThrow(() -> new BusinessException("No se encontro la compra relacionada para resolver el proveedor del lote."));
        }

        if (destino.getProveedor() != null) {
            return destino.getProveedor();
        }

        return null;
    }

    private String normalizarCodigo(String valor, String mensaje) {
        String limpio = SanitizadorTexto.limpiar(valor);
        if (limpio == null) {
            throw new BusinessException(mensaje);
        }
        return limpio.replace(' ', '-').toUpperCase(Locale.ROOT);
    }

    private int extraerSiguienteCorrelativoProveedor(String ultimoCodigo) {
        if (ultimoCodigo == null || ultimoCodigo.isBlank()) {
            return 1;
        }

        String sufijo = ultimoCodigo.replaceAll("^.*?(\\d+)$", "$1");
        if (sufijo.equals(ultimoCodigo) && !ultimoCodigo.matches(".*\\d+$")) {
            return 1;
        }

        try {
            return Integer.parseInt(sufijo) + 1;
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private double redondearDosDecimales(double valor) {
        return Math.round(valor * 100D) / 100D;
    }

    private boolean contieneTexto(String valor, String filtro) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(filtro.trim().toLowerCase(Locale.ROOT));
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
}
