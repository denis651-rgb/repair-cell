package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CuentaPorCobrar;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.EstadoCuentaPorCobrar;
import com.store.repair.domain.EstadoVenta;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.ProductoBase;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.TipoPagoVenta;
import com.store.repair.domain.Venta;
import com.store.repair.domain.VentaDetalle;
import com.store.repair.domain.VentaDetalleLote;
import com.store.repair.dto.DevolucionVentaDetalleRequest;
import com.store.repair.dto.DevolucionVentaRequest;
import com.store.repair.dto.VentaListadoResponse;
import com.store.repair.dto.VentaDetalleRegistroRequest;
import com.store.repair.dto.VentaRegistroRequest;
import com.store.repair.repository.CuentaPorCobrarRepository;
import com.store.repair.repository.EntradaContableRepository;
import com.store.repair.repository.LoteInventarioRepository;
import com.store.repair.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VentaService {

    private final VentaRepository repository;
    private final ClienteService clienteService;
    private final ProductoVarianteService varianteService;
    private final LoteInventarioRepository loteRepository;
    private final AccountingService accountingService;
    private final EntradaContableRepository entradaContableRepository;
    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;
    private final ComprobanteService comprobanteService;

    @Transactional(readOnly = true)
    public Page<VentaListadoResponse> findPage(String busqueda, int pagina, int tamano) {
        String textoBusqueda = busqueda == null ? "" : busqueda.trim();
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.max(tamano, 1);

        try {
            return repository.search(
                    textoBusqueda,
                    PageRequest.of(paginaSegura, tamanoSeguro));

        } catch (ResourceNotFoundException | BusinessException ex) {
            log.warn(
                    "Error controlado al paginar ventas. busqueda='{}', pagina={}, tamano={}. Motivo={}",
                    textoBusqueda, paginaSegura, tamanoSeguro, ex.getMessage(), ex);
            throw ex;

        } catch (DataAccessException ex) {
            log.error(
                    "Error de base de datos al paginar ventas. busqueda='{}', pagina={}, tamano={}",
                    textoBusqueda, paginaSegura, tamanoSeguro, ex);
            throw new BusinessException("Ocurrio un error al consultar las ventas en la base de datos.");

        } catch (Exception ex) {
            log.error(
                    "Error inesperado en findPage(). busqueda='{}', pagina={}, tamano={}",
                    textoBusqueda, paginaSegura, tamanoSeguro, ex);
            throw new RuntimeException("Error inesperado al obtener el listado paginado de ventas.", ex);
        }
    }

    public Venta findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + id));
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public Venta registrarVenta(VentaRegistroRequest solicitud) {
        List<VentaDetalleRegistroRequest> detallesNormalizados = normalizarDetallesVenta(solicitud.getDetalles());
        if (detallesNormalizados.isEmpty()) {
            throw new BusinessException("La venta debe incluir al menos una variante");
        }

        Venta venta = Venta.builder()
                .cliente(clienteService.findById(solicitud.getClienteId()))
                .fechaVenta(solicitud.getFechaVenta() == null ? LocalDate.now() : solicitud.getFechaVenta())
                .numeroComprobante(obtenerNumeroComprobante(solicitud.getNumeroComprobante()))
                .observaciones(SanitizadorTexto.limpiar(solicitud.getObservaciones()))
                .tipoPago(solicitud.getTipoPago())
                .estado(EstadoVenta.REGISTRADA)
                .detalles(new ArrayList<>())
                .build();

        Venta ventaGuardada = repository.save(venta);

        double totalVenta = 0D;
        List<VentaDetalle> detallesGuardados = new ArrayList<>();

        for (VentaDetalleRegistroRequest detalleSolicitud : detallesNormalizados) {
            ProductoVariante variante = varianteService.findById(detalleSolicitud.getVarianteId());
            ProductoBase productoBase = variante.getProductoBase();
            double precioLista = detalleSolicitud.getPrecioListaUnitario() != null
                    ? detalleSolicitud.getPrecioListaUnitario()
                    : (variante.getPrecioVentaSugerido() == null ? 0D : variante.getPrecioVentaSugerido());
            double precioVenta = detalleSolicitud.getPrecioVentaUnitario() != null
                    ? detalleSolicitud.getPrecioVentaUnitario()
                    : precioLista;
            double subtotal = detalleSolicitud.getCantidad() * precioVenta;

            VentaDetalle detalle = VentaDetalle.builder()
                    .venta(ventaGuardada)
                    .producto(null)
                    .variante(varianteService.findById(variante.getId()))
                    .categoriaNombre(productoBase.getCategoria().getNombre())
                    .sku(variante.getCodigoVariante())
                    .nombreProducto(productoBase.getNombreBase())
                    .productoBaseCodigo(productoBase.getCodigoBase())
                    .marca(productoBase.getMarca().getNombre())
                    .calidad(variante.getCalidad())
                    .tipoPresentacion(variante.getTipoPresentacion())
                    .color(variante.getColor())
                    .cantidad(detalleSolicitud.getCantidad())
                    .cantidadDevuelta(0)
                    .precioListaUnitario(precioLista)
                    .precioVentaUnitario(precioVenta)
                    .subtotal(subtotal)
                    .detallesLote(new ArrayList<>())
                    .build();

            List<VentaDetalleLote> consumos = consumirLotesFifo(variante, detalle, detalleSolicitud.getCantidad(), precioVenta);
            detalle.replaceDetallesLote(consumos);
            detallesGuardados.add(detalle);
            totalVenta += subtotal;
        }

        ventaGuardada.replaceDetalles(detallesGuardados);
        ventaGuardada.setTotal(totalVenta);
        Venta ventaFinal = repository.save(ventaGuardada);

        if (ventaFinal.getTipoPago() == TipoPagoVenta.CONTADO) {
            registrarEntradaPorVenta(ventaFinal);
        } else {
            crearCuentaPorCobrar(ventaFinal);
        }

        return findById(ventaFinal.getId());
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public Venta devolverVenta(Long ventaId, DevolucionVentaRequest request) {
        Venta venta = findById(ventaId);

        if (venta.getEstado() == EstadoVenta.DEVUELTA) {
            throw new BusinessException("La venta ya fue devuelta por completo");
        }

        List<DevolucionVentaDetalleRequest> detallesNormalizados = normalizarDetallesDevolucion(venta, request);
        double montoDevueltoEnOperacion = 0D;

        for (DevolucionVentaDetalleRequest detalleSolicitud : detallesNormalizados) {
            VentaDetalle detalle = buscarDetalleVenta(venta, detalleSolicitud.getVentaDetalleId());
            int cantidadYaDevuelta = detalle.getCantidadDevuelta() == null ? 0 : detalle.getCantidadDevuelta();

            detalle.setCantidadDevuelta(cantidadYaDevuelta + detalleSolicitud.getCantidad());
            montoDevueltoEnOperacion += detalleSolicitud.getCantidad() * detalle.getPrecioVentaUnitario();
            restaurarLotesPorDevolucion(detalle, detalleSolicitud.getCantidad());
        }

        venta.setEstado(estaVentaTotalmenteDevuelta(venta) ? EstadoVenta.DEVUELTA : EstadoVenta.PARCIALMENTE_DEVUELTA);
        venta.setFechaDevolucion(request.getFechaDevolucion() == null ? LocalDate.now() : request.getFechaDevolucion());
        venta.setMotivoDevolucion(SanitizadorTexto.limpiar(request.getMotivoDevolucion()));
        Venta ventaActualizada = repository.save(venta);

        if (ventaActualizada.getTipoPago() == TipoPagoVenta.CONTADO) {
            registrarSalidaPorDevolucion(ventaActualizada, calcularTotalDevuelto(ventaActualizada));
        } else {
            ajustarCuentaPorCobrarPorDevolucion(ventaActualizada, montoDevueltoEnOperacion);
        }

        return findById(ventaId);
    }

    private List<VentaDetalleRegistroRequest> normalizarDetallesVenta(List<VentaDetalleRegistroRequest> detallesOriginales) {
        if (detallesOriginales == null || detallesOriginales.isEmpty()) {
            return List.of();
        }

        Map<Long, VentaDetalleRegistroRequest> detallesConsolidados = new LinkedHashMap<>();

        for (VentaDetalleRegistroRequest detalle : detallesOriginales) {
            validarDetalleVenta(detalle);

            VentaDetalleRegistroRequest existente = detallesConsolidados.get(detalle.getVarianteId());
            if (existente == null) {
                detallesConsolidados.put(detalle.getVarianteId(), clonarDetalle(detalle));
                continue;
            }

            existente.setCantidad(existente.getCantidad() + detalle.getCantidad());
            if (detalle.getPrecioVentaUnitario() != null) {
                existente.setPrecioVentaUnitario(detalle.getPrecioVentaUnitario());
            }
            if (detalle.getPrecioListaUnitario() != null) {
                existente.setPrecioListaUnitario(detalle.getPrecioListaUnitario());
            }
        }

        for (VentaDetalleRegistroRequest detalleConsolidado : detallesConsolidados.values()) {
            int stockDisponible = obtenerStockTotalDisponible(detalleConsolidado.getVarianteId());
            ProductoVariante variante = varianteService.findById(detalleConsolidado.getVarianteId());
            if (detalleConsolidado.getCantidad() > stockDisponible) {
                throw new BusinessException(
                        "Stock insuficiente para " + variante.getCodigoVariante() + ". Disponible: " + stockDisponible
                                + ", solicitado: " + detalleConsolidado.getCantidad());
            }
        }

        return new ArrayList<>(detallesConsolidados.values());
    }

    private List<DevolucionVentaDetalleRequest> normalizarDetallesDevolucion(Venta venta, DevolucionVentaRequest request) {
        if (request == null || request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new BusinessException("Selecciona al menos un item para la devolucion");
        }

        Map<Long, DevolucionVentaDetalleRequest> detallesConsolidados = new LinkedHashMap<>();

        for (DevolucionVentaDetalleRequest detalle : request.getDetalles()) {
            if (detalle == null || detalle.getVentaDetalleId() == null) {
                throw new BusinessException("Cada item de la devolucion debe indicar la linea de venta");
            }
            if (detalle.getCantidad() == null || detalle.getCantidad() <= 0) {
                throw new BusinessException("La cantidad a devolver debe ser mayor a cero");
            }

            VentaDetalle detalleVenta = buscarDetalleVenta(venta, detalle.getVentaDetalleId());
            DevolucionVentaDetalleRequest existente = detallesConsolidados.get(detalle.getVentaDetalleId());
            if (existente == null) {
                DevolucionVentaDetalleRequest copia = new DevolucionVentaDetalleRequest();
                copia.setVentaDetalleId(detalle.getVentaDetalleId());
                copia.setCantidad(detalle.getCantidad());
                detallesConsolidados.put(detalle.getVentaDetalleId(), copia);
            } else {
                existente.setCantidad(existente.getCantidad() + detalle.getCantidad());
            }

            int cantidadVendida = detalleVenta.getCantidad() == null ? 0 : detalleVenta.getCantidad();
            int cantidadYaDevuelta = detalleVenta.getCantidadDevuelta() == null ? 0 : detalleVenta.getCantidadDevuelta();
            int disponibleParaDevolver = cantidadVendida - cantidadYaDevuelta;
            int cantidadSolicitada = detallesConsolidados.get(detalle.getVentaDetalleId()).getCantidad();

            if (cantidadSolicitada > disponibleParaDevolver) {
                throw new BusinessException(
                        "La devolucion para " + detalleVenta.getNombreProducto() + " supera lo disponible. Disponible: "
                                + disponibleParaDevolver + ", solicitado: " + cantidadSolicitada);
            }
        }

        return new ArrayList<>(detallesConsolidados.values());
    }

    private void validarDetalleVenta(VentaDetalleRegistroRequest detalle) {
        if (detalle == null || detalle.getVarianteId() == null) {
            throw new BusinessException("Cada item de la venta debe tener una variante seleccionada");
        }

        if (detalle.getCantidad() == null || detalle.getCantidad() <= 0) {
            throw new BusinessException("Cada item de la venta debe tener una cantidad mayor a cero");
        }

        if (detalle.getPrecioVentaUnitario() != null && detalle.getPrecioVentaUnitario() < 0) {
            throw new BusinessException("El precio real de venta no puede ser negativo");
        }
    }

    private VentaDetalleRegistroRequest clonarDetalle(VentaDetalleRegistroRequest origen) {
        VentaDetalleRegistroRequest copia = new VentaDetalleRegistroRequest();
        copia.setVarianteId(origen.getVarianteId());
        copia.setCantidad(origen.getCantidad());
        copia.setPrecioListaUnitario(origen.getPrecioListaUnitario());
        copia.setPrecioVentaUnitario(origen.getPrecioVentaUnitario());
        return copia;
    }

    private VentaDetalle buscarDetalleVenta(Venta venta, Long detalleId) {
        return venta.getDetalles().stream()
                .filter(detalle -> detalle.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Uno de los items no pertenece a la venta seleccionada"));
    }

    private List<VentaDetalleLote> consumirLotesFifo(ProductoVariante variante, VentaDetalle detalle, int cantidadSolicitada, double precioVentaUnitario) {
        List<LoteInventario> lotes = loteRepository.findConsumiblesFifoByVarianteId(variante.getId());
        int restante = cantidadSolicitada;
        List<VentaDetalleLote> consumos = new ArrayList<>();

        for (LoteInventario lote : lotes) {
            if (restante <= 0) {
                break;
            }

            if (lote.getVariante() == null || !lote.getVariante().getId().equals(variante.getId())) {
                throw new BusinessException(
                        "El lote " + lote.getCodigoLote()
                                + " no pertenece a la variante " + variante.getCodigoVariante() + ".");
            }

            int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
            if (disponible <= 0) {
                continue;
            }

            int cantidadTomada = Math.min(disponible, restante);
            lote.setCantidadDisponible(disponible - cantidadTomada);
            actualizarEstadoLoteTrasMovimiento(lote);
            loteRepository.save(lote);

            double costoUnitario = lote.getCostoUnitario() == null ? 0D : lote.getCostoUnitario();
            double costoTotal = redondear(costoUnitario * cantidadTomada);
            double gananciaBruta = redondear((precioVentaUnitario - costoUnitario) * cantidadTomada);

            consumos.add(VentaDetalleLote.builder()
                    .ventaDetalle(detalle)
                    .lote(lote)
                    .cantidad(cantidadTomada)
                    .cantidadDevuelta(0)
                    .costoUnitarioAplicado(costoUnitario)
                    .costoTotal(costoTotal)
                    .gananciaBruta(gananciaBruta)
                    .build());

            restante -= cantidadTomada;
        }

        if (restante > 0) {
            throw new BusinessException(
                    "Stock insuficiente para " + variante.getCodigoVariante() + ". Faltan " + restante + " unidades.");
        }

        return consumos;
    }

    private void restaurarLotesPorDevolucion(VentaDetalle detalle, int cantidadADevolver) {
        int restante = cantidadADevolver;

        for (VentaDetalleLote detalleLote : detalle.getDetallesLote()) {
            if (restante <= 0) {
                break;
            }

            int vendido = detalleLote.getCantidad() == null ? 0 : detalleLote.getCantidad();
            int yaDevuelto = detalleLote.getCantidadDevuelta() == null ? 0 : detalleLote.getCantidadDevuelta();
            int disponibleParaDevolver = vendido - yaDevuelto;
            if (disponibleParaDevolver <= 0) {
                continue;
            }

            int cantidadRestituida = Math.min(disponibleParaDevolver, restante);
            detalleLote.setCantidadDevuelta(yaDevuelto + cantidadRestituida);

            LoteInventario lote = detalleLote.getLote();
            if (detalle.getVariante() != null
                    && lote.getVariante() != null
                    && !detalle.getVariante().getId().equals(lote.getVariante().getId())) {
                throw new BusinessException(
                        "Se detecto una mezcla invalida entre la variante vendida y el lote "
                                + lote.getCodigoLote() + " durante la devolucion.");
            }
            int disponibleActual = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
            lote.setCantidadDisponible(disponibleActual + cantidadRestituida);
            lote.setActivo(Boolean.TRUE);
            lote.setVisibleEnVentas(Boolean.TRUE);
            lote.setFechaCierre(null);
            lote.setEstado(com.store.repair.domain.EstadoLoteInventario.ACTIVO);
            lote.setMotivoCierre(null);
            loteRepository.save(lote);

            restante -= cantidadRestituida;
        }

        if (restante > 0) {
            throw new BusinessException("No se pudo restituir toda la devolucion a los lotes originales.");
        }
    }

    private int obtenerStockTotalDisponible(Long varianteId) {
        Integer total = loteRepository.sumStockDisponibleActivoByVarianteId(varianteId);
        return total == null ? 0 : total;
    }

    private void actualizarEstadoLoteTrasMovimiento(LoteInventario lote) {
        int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        if (disponible <= 0) {
            lote.setCantidadDisponible(0);
            lote.setEstado(com.store.repair.domain.EstadoLoteInventario.AGOTADO);
            lote.setVisibleEnVentas(Boolean.FALSE);
            lote.setFechaCierre(LocalDateTime.now());
        } else {
            lote.setEstado(com.store.repair.domain.EstadoLoteInventario.ACTIVO);
            lote.setActivo(Boolean.TRUE);
            lote.setVisibleEnVentas(Boolean.TRUE);
            lote.setFechaCierre(null);
        }
    }

    private void crearCuentaPorCobrar(Venta venta) {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder()
                .cliente(venta.getCliente())
                .venta(venta)
                .fechaEmision(venta.getFechaVenta())
                .montoOriginal(venta.getTotal())
                .saldoPendiente(venta.getTotal())
                .estado(EstadoCuentaPorCobrar.PENDIENTE)
                .build();

        cuentaPorCobrarRepository.save(cuenta);
    }

    private void ajustarCuentaPorCobrarPorDevolucion(Venta venta, double montoDevuelto) {
        cuentaPorCobrarRepository.findByVentaId(venta.getId()).ifPresent(cuenta -> {
            double montoOriginalActual = cuenta.getMontoOriginal() == null ? 0D : cuenta.getMontoOriginal();
            double saldoPendienteActual = cuenta.getSaldoPendiente() == null ? 0D : cuenta.getSaldoPendiente();

            if (montoDevuelto > saldoPendienteActual) {
                throw new BusinessException(
                        "La devolucion supera el saldo pendiente del credito. Registra primero un ajuste financiero.");
            }

            double montoOriginalNuevo = Math.max(montoOriginalActual - montoDevuelto, 0D);
            double saldoPendienteNuevo = Math.max(saldoPendienteActual - montoDevuelto, 0D);

            cuenta.setMontoOriginal(montoOriginalNuevo);
            cuenta.setSaldoPendiente(saldoPendienteNuevo);

            if (montoOriginalNuevo <= 0) {
                cuenta.setEstado(EstadoCuentaPorCobrar.ANULADA);
            } else if (saldoPendienteNuevo <= 0) {
                cuenta.setEstado(EstadoCuentaPorCobrar.PAGADA);
            } else if (saldoPendienteNuevo < montoOriginalNuevo) {
                cuenta.setEstado(EstadoCuentaPorCobrar.PARCIAL);
            } else {
                cuenta.setEstado(EstadoCuentaPorCobrar.PENDIENTE);
            }

            cuentaPorCobrarRepository.save(cuenta);
        });
    }

    private void registrarEntradaPorVenta(Venta venta) {
        EntradaContable entrada = entradaContableRepository
                .findFirstByModuloRelacionadoAndRelacionadoId("VENTA", venta.getId())
                .orElseGet(() -> EntradaContable.builder()
                        .moduloRelacionado("VENTA")
                        .relacionadoId(venta.getId())
                        .tipoEntrada(TipoEntrada.ENTRADA)
                        .build());

        entrada.setCategoria("VENTA_PRODUCTOS");
        entrada.setDescripcion("Venta " + referenciaVenta(venta) + " a " + venta.getCliente().getNombreCompleto());
        entrada.setMonto(venta.getTotal());
        entrada.setFechaEntrada(venta.getFechaVenta());
        accountingService.save(entrada);
    }

    private void registrarSalidaPorDevolucion(Venta venta, double montoDevueltoAcumulado) {
        EntradaContable entrada = entradaContableRepository
                .findFirstByModuloRelacionadoAndRelacionadoId("DEVOLUCION_VENTA", venta.getId())
                .orElseGet(() -> EntradaContable.builder()
                        .moduloRelacionado("DEVOLUCION_VENTA")
                        .relacionadoId(venta.getId())
                        .tipoEntrada(TipoEntrada.SALIDA)
                        .build());

        entrada.setCategoria("DEVOLUCION_VENTA");
        entrada.setDescripcion("Devolucion de venta " + referenciaVenta(venta));
        entrada.setMonto(montoDevueltoAcumulado);
        entrada.setFechaEntrada(venta.getFechaDevolucion() == null ? LocalDate.now() : venta.getFechaDevolucion());
        accountingService.save(entrada);
    }

    private boolean estaVentaTotalmenteDevuelta(Venta venta) {
        return venta.getDetalles().stream().allMatch(detalle -> {
            int cantidadVendida = detalle.getCantidad() == null ? 0 : detalle.getCantidad();
            int cantidadDevuelta = detalle.getCantidadDevuelta() == null ? 0 : detalle.getCantidadDevuelta();
            return cantidadDevuelta >= cantidadVendida;
        });
    }

    private double calcularTotalDevuelto(Venta venta) {
        return redondear(venta.getDetalles().stream()
                .mapToDouble(detalle -> (detalle.getCantidadDevuelta() == null ? 0 : detalle.getCantidadDevuelta())
                        * (detalle.getPrecioVentaUnitario() == null ? 0D : detalle.getPrecioVentaUnitario()))
                .sum());
    }

    private String referenciaVenta(Venta venta) {
        return venta.getNumeroComprobante() != null ? venta.getNumeroComprobante() : ("#" + venta.getId());
    }

    private double redondear(double valor) {
        return Math.round(valor * 100D) / 100D;
    }

    private String obtenerNumeroComprobante(String numeroRecibido) {
        String numeroNormalizado = SanitizadorTexto.limpiar(numeroRecibido);
        return numeroNormalizado != null ? numeroNormalizado : comprobanteService.generarNumeroComprobante();
    }
}
