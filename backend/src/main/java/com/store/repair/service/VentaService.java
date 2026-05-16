package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CuentaPorCobrar;
import com.store.repair.domain.DevolucionVenta;
import com.store.repair.domain.DevolucionVentaDetalle;
import com.store.repair.domain.DevolucionVentaDetalleLote;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.EstadoCuentaPorCobrar;
import com.store.repair.domain.EstadoLoteInventario;
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
import com.store.repair.dto.LoteVentaOptionResponse;
import com.store.repair.dto.VentaListadoResponse;
import com.store.repair.dto.VentaDetalleRegistroRequest;
import com.store.repair.dto.VentaRegistroRequest;
import com.store.repair.repository.CuentaPorCobrarRepository;
import com.store.repair.repository.DevolucionVentaRepository;
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
    private final DevolucionVentaRepository devolucionVentaRepository;
    private final ComprobanteService comprobanteService;

    @Transactional(readOnly = true)
    public List<LoteVentaOptionResponse> listarLotesDisponiblesParaVenta(Long varianteId) {
        varianteService.findById(varianteId);
        return loteRepository.findOpcionesVentaByVarianteId(varianteId).stream()
                .map(this::toLoteVentaOptionResponse)
                .toList();
    }

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

    @Transactional(readOnly = true)
    public List<DevolucionVenta> listarDevoluciones(Long ventaId) {
        findById(ventaId);
        return devolucionVentaRepository.findByVentaIdOrderByFechaDevolucionDescIdDesc(ventaId);
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
            LoteInventario loteSeleccionado = detalleSolicitud.getLoteId() == null
                    ? null
                    : obtenerLoteSeleccionado(variante, detalleSolicitud.getLoteId(), detalleSolicitud.getCantidad());
            double precioLista = detalleSolicitud.getPrecioListaUnitario() != null
                    ? detalleSolicitud.getPrecioListaUnitario()
                    : resolverPrecioLista(variante, loteSeleccionado);
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

            List<VentaDetalleLote> consumos = loteSeleccionado == null
                    ? consumirLotesFifo(variante, detalle, detalleSolicitud.getCantidad(), precioVenta)
                    : consumirLoteSeleccionado(loteSeleccionado, detalle, detalleSolicitud.getCantidad(), precioVenta);
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
        LocalDate fechaDevolucion = request.getFechaDevolucion() == null ? LocalDate.now() : request.getFechaDevolucion();
        String motivoDevolucion = SanitizadorTexto.limpiar(request.getMotivoDevolucion());
        DevolucionVenta devolucion = DevolucionVenta.builder()
                .venta(venta)
                .fechaDevolucion(fechaDevolucion)
                .motivoDevolucion(motivoDevolucion == null ? "Sin motivo registrado" : motivoDevolucion)
                .tipoPagoVenta(venta.getTipoPago())
                .detalles(new ArrayList<>())
                .build();

        for (DevolucionVentaDetalleRequest detalleSolicitud : detallesNormalizados) {
            VentaDetalle detalle = buscarDetalleVenta(venta, detalleSolicitud.getVentaDetalleId());
            int cantidadYaDevuelta = detalle.getCantidadDevuelta() == null ? 0 : detalle.getCantidadDevuelta();
            double subtotalDevolucion = redondear(detalleSolicitud.getCantidad() * detalle.getPrecioVentaUnitario());

            detalle.setCantidadDevuelta(cantidadYaDevuelta + detalleSolicitud.getCantidad());
            montoDevueltoEnOperacion += subtotalDevolucion;

            DevolucionVentaDetalle detalleDevolucion = DevolucionVentaDetalle.builder()
                    .ventaDetalle(detalle)
                    .cantidad(detalleSolicitud.getCantidad())
                    .precioVentaUnitario(detalle.getPrecioVentaUnitario())
                    .subtotal(subtotalDevolucion)
                    .detallesLote(new ArrayList<>())
                    .build();
            detalleDevolucion.replaceDetallesLote(
                    restaurarLotesPorDevolucion(detalle, detalleSolicitud.getCantidad()));
            devolucion.addDetalle(detalleDevolucion);
        }

        venta.setEstado(estaVentaTotalmenteDevuelta(venta) ? EstadoVenta.DEVUELTA : EstadoVenta.PARCIALMENTE_DEVUELTA);
        venta.setFechaDevolucion(fechaDevolucion);
        venta.setMotivoDevolucion(motivoDevolucion);
        Venta ventaActualizada = repository.save(venta);
        double montoAplicadoCuentaPorCobrar = 0D;
        double montoReembolsado = 0D;

        if (ventaActualizada.getTipoPago() == TipoPagoVenta.CONTADO) {
            montoReembolsado = redondear(montoDevueltoEnOperacion);
            registrarSalidaPorDevolucion(ventaActualizada, calcularTotalDevuelto(ventaActualizada));
        } else {
            AjusteCreditoDevolucion ajuste = ajustarCuentaPorCobrarPorDevolucion(ventaActualizada, montoDevueltoEnOperacion);
            montoAplicadoCuentaPorCobrar = ajuste.montoAplicadoCuentaPorCobrar();
            montoReembolsado = ajuste.montoReembolsado();
            if (montoReembolsado > 0) {
                registrarSalidaPorDevolucion(
                        ventaActualizada,
                        calcularTotalReembolsadoPorDevoluciones(ventaActualizada.getId()) + montoReembolsado);
            }
        }

        devolucion.setMontoTotal(redondear(montoDevueltoEnOperacion));
        devolucion.setMontoAplicadoCuentaPorCobrar(montoAplicadoCuentaPorCobrar);
        devolucion.setMontoReembolsado(montoReembolsado);
        devolucionVentaRepository.save(devolucion);

        return findById(ventaId);
    }

    private List<VentaDetalleRegistroRequest> normalizarDetallesVenta(List<VentaDetalleRegistroRequest> detallesOriginales) {
        if (detallesOriginales == null || detallesOriginales.isEmpty()) {
            return List.of();
        }

        Map<String, VentaDetalleRegistroRequest> detallesConsolidados = new LinkedHashMap<>();

        for (VentaDetalleRegistroRequest detalle : detallesOriginales) {
            validarDetalleVenta(detalle);

            String llave = construirLlaveDetalleVenta(detalle);
            VentaDetalleRegistroRequest existente = detallesConsolidados.get(llave);
            if (existente == null) {
                detallesConsolidados.put(llave, clonarDetalle(detalle));
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
            ProductoVariante variante = varianteService.findById(detalleConsolidado.getVarianteId());
            int stockDisponible = detalleConsolidado.getLoteId() == null
                    ? obtenerStockTotalDisponible(detalleConsolidado.getVarianteId())
                    : obtenerLoteSeleccionado(variante, detalleConsolidado.getLoteId(), detalleConsolidado.getCantidad())
                            .getCantidadDisponible();
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

        if (detalle.getPrecioListaUnitario() != null && detalle.getPrecioListaUnitario() < 0) {
            throw new BusinessException("El precio de lista no puede ser negativo");
        }
    }

    private VentaDetalleRegistroRequest clonarDetalle(VentaDetalleRegistroRequest origen) {
        VentaDetalleRegistroRequest copia = new VentaDetalleRegistroRequest();
        copia.setVarianteId(origen.getVarianteId());
        copia.setLoteId(origen.getLoteId());
        copia.setCantidad(origen.getCantidad());
        copia.setPrecioListaUnitario(origen.getPrecioListaUnitario());
        copia.setPrecioVentaUnitario(origen.getPrecioVentaUnitario());
        return copia;
    }

    private String construirLlaveDetalleVenta(VentaDetalleRegistroRequest detalle) {
        return detalle.getVarianteId()
                + "|" + (detalle.getLoteId() == null ? "FIFO" : detalle.getLoteId())
                + "|" + detalle.getPrecioListaUnitario()
                + "|" + detalle.getPrecioVentaUnitario();
    }

    private VentaDetalle buscarDetalleVenta(Venta venta, Long detalleId) {
        return venta.getDetalles().stream()
                .filter(detalle -> detalle.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Uno de los items no pertenece a la venta seleccionada"));
    }

    private LoteInventario obtenerLoteSeleccionado(ProductoVariante variante, Long loteId, int cantidadSolicitada) {
        LoteInventario lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado: " + loteId));

        if (lote.getVariante() == null || !lote.getVariante().getId().equals(variante.getId())) {
            throw new BusinessException(
                    "El lote " + lote.getCodigoLote()
                            + " no pertenece a la variante " + variante.getCodigoVariante() + ".");
        }

        if (!Boolean.TRUE.equals(lote.getActivo())
                || !Boolean.TRUE.equals(lote.getVisibleEnVentas())
                || lote.getEstado() != EstadoLoteInventario.ACTIVO) {
            throw new BusinessException("El lote " + lote.getCodigoLote() + " no esta disponible para ventas.");
        }

        int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        if (cantidadSolicitada > disponible) {
            throw new BusinessException(
                    "Stock insuficiente en el lote " + lote.getCodigoLote() + ". Disponible: " + disponible
                            + ", solicitado: " + cantidadSolicitada);
        }

        return lote;
    }

    private double resolverPrecioLista(ProductoVariante variante, LoteInventario loteSeleccionado) {
        if (loteSeleccionado != null && loteSeleccionado.getPrecioVentaUnitario() != null
                && loteSeleccionado.getPrecioVentaUnitario() > 0) {
            return loteSeleccionado.getPrecioVentaUnitario();
        }
        if (variante == null) {
            return 0D;
        }
        return variante.getPrecioVentaSugerido() == null ? 0D : variante.getPrecioVentaSugerido();
    }

    private List<VentaDetalleLote> consumirLoteSeleccionado(
            LoteInventario lote,
            VentaDetalle detalle,
            int cantidadSolicitada,
            double precioVentaUnitario) {
        int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        if (cantidadSolicitada > disponible) {
            throw new BusinessException(
                    "Stock insuficiente en el lote " + lote.getCodigoLote() + ". Disponible: " + disponible
                            + ", solicitado: " + cantidadSolicitada);
        }

        lote.setCantidadDisponible(disponible - cantidadSolicitada);
        actualizarEstadoLoteTrasMovimiento(lote);
        loteRepository.save(lote);

        double costoUnitario = lote.getCostoUnitario() == null ? 0D : lote.getCostoUnitario();
        double costoTotal = redondear(costoUnitario * cantidadSolicitada);
        double gananciaBruta = redondear((precioVentaUnitario - costoUnitario) * cantidadSolicitada);

        return List.of(VentaDetalleLote.builder()
                .ventaDetalle(detalle)
                .lote(lote)
                .cantidad(cantidadSolicitada)
                .cantidadDevuelta(0)
                .costoUnitarioAplicado(costoUnitario)
                .costoTotal(costoTotal)
                .gananciaBruta(gananciaBruta)
                .build());
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

    private List<DevolucionVentaDetalleLote> restaurarLotesPorDevolucion(VentaDetalle detalle, int cantidadADevolver) {
        int restante = cantidadADevolver;
        List<DevolucionVentaDetalleLote> detallesLoteDevolucion = new ArrayList<>();

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

            double costoUnitario = detalleLote.getCostoUnitarioAplicado() == null ? 0D : detalleLote.getCostoUnitarioAplicado();
            double precioVentaUnitario = detalle.getPrecioVentaUnitario() == null ? 0D : detalle.getPrecioVentaUnitario();
            detallesLoteDevolucion.add(DevolucionVentaDetalleLote.builder()
                    .ventaDetalleLote(detalleLote)
                    .lote(lote)
                    .cantidad(cantidadRestituida)
                    .costoUnitarioAplicado(costoUnitario)
                    .costoTotal(redondear(costoUnitario * cantidadRestituida))
                    .gananciaRevertida(redondear((precioVentaUnitario - costoUnitario) * cantidadRestituida))
                    .build());

            restante -= cantidadRestituida;
        }

        if (restante > 0) {
            throw new BusinessException("No se pudo restituir toda la devolucion a los lotes originales.");
        }

        return detallesLoteDevolucion;
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

    private AjusteCreditoDevolucion ajustarCuentaPorCobrarPorDevolucion(Venta venta, double montoDevuelto) {
        return cuentaPorCobrarRepository.findByVentaId(venta.getId()).map(cuenta -> {
            double montoOriginalActual = cuenta.getMontoOriginal() == null ? 0D : cuenta.getMontoOriginal();
            double saldoPendienteActual = cuenta.getSaldoPendiente() == null ? 0D : cuenta.getSaldoPendiente();
            double montoAplicadoCuentaPorCobrar = redondear(Math.min(montoDevuelto, saldoPendienteActual));
            double montoReembolsado = redondear(Math.max(montoDevuelto - saldoPendienteActual, 0D));

            double montoOriginalNuevo = Math.max(montoOriginalActual - montoDevuelto, 0D);
            double saldoPendienteNuevo = Math.max(saldoPendienteActual - montoAplicadoCuentaPorCobrar, 0D);

            cuenta.setMontoOriginal(redondear(montoOriginalNuevo));
            cuenta.setSaldoPendiente(redondear(saldoPendienteNuevo));

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
            return new AjusteCreditoDevolucion(montoAplicadoCuentaPorCobrar, montoReembolsado);
        }).orElseGet(() -> new AjusteCreditoDevolucion(0D, redondear(montoDevuelto)));
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

    private double calcularTotalReembolsadoPorDevoluciones(Long ventaId) {
        return redondear(devolucionVentaRepository.findByVentaIdOrderByFechaDevolucionDescIdDesc(ventaId).stream()
                .mapToDouble(devolucion -> devolucion.getMontoReembolsado() == null ? 0D : devolucion.getMontoReembolsado())
                .sum());
    }

    private String referenciaVenta(Venta venta) {
        return venta.getNumeroComprobante() != null ? venta.getNumeroComprobante() : ("#" + venta.getId());
    }

    private LoteVentaOptionResponse toLoteVentaOptionResponse(LoteInventario lote) {
        double costo = lote.getCostoUnitario() == null ? 0D : lote.getCostoUnitario();
        double precioVenta = lote.getPrecioVentaUnitario() != null && lote.getPrecioVentaUnitario() > 0
                ? lote.getPrecioVentaUnitario()
                : resolverPrecioLista(lote.getVariante(), lote);
        return LoteVentaOptionResponse.builder()
                .id(lote.getId())
                .varianteId(lote.getVariante() == null ? null : lote.getVariante().getId())
                .codigoLote(lote.getCodigoLote())
                .codigoProveedor(lote.getCodigoProveedor())
                .fechaIngreso(lote.getFechaIngreso())
                .cantidadDisponible(lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible())
                .costoUnitario(costo)
                .precioVentaUnitario(precioVenta)
                .gananciaUnitaria(redondear(precioVenta - costo))
                .proveedorNombre(lote.getProveedor() == null ? null : lote.getProveedor().getNombreComercial())
                .build();
    }

    private double redondear(double valor) {
        return Math.round(valor * 100D) / 100D;
    }

    private String obtenerNumeroComprobante(String numeroRecibido) {
        String numeroNormalizado = SanitizadorTexto.limpiar(numeroRecibido);
        return numeroNormalizado != null ? numeroNormalizado : comprobanteService.generarNumeroComprobante();
    }

    private record AjusteCreditoDevolucion(double montoAplicadoCuentaPorCobrar, double montoReembolsado) {
    }
}
