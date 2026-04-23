package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.Cliente;
import com.store.repair.domain.Dispositivo;
import com.store.repair.domain.EstadoReparacion;
import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.ParteOrdenReparacion;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.domain.TipoFuenteParte;
import com.store.repair.domain.TipoMovimientoStock;
import com.store.repair.domain.LoteInventario;
import com.store.repair.dto.OrdenReparacionRequest;
import com.store.repair.dto.ParteOrdenReparacionRequest;
import com.store.repair.repository.OrdenReparacionRepository;
import com.store.repair.repository.LoteInventarioRepository;
import com.store.repair.util.OrdenMontoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrdenReparacionService {

    private final OrdenReparacionRepository repository;
    private final ClienteService clienteService;
    private final DispositivoService dispositivoService;
    private final ProductoInventarioService productoInventarioService;
    private final ProductoVarianteService productoVarianteService;
    private final LoteInventarioRepository loteInventarioRepository;
    private final AccountingService accountingService;
    private final HistoryService historyService;

    public List<OrdenReparacion> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "recibidoEn"));
    }

    public Page<OrdenReparacion> findPage(String busqueda, int pagina, int tamano) {
        String termino = busqueda == null ? "" : busqueda.trim();
        return repository
                .findByNumeroOrdenContainingIgnoreCaseOrClienteNombreCompletoContainingIgnoreCaseOrderByRecibidoEnDesc(
                        termino,
                        termino,
                        PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
    }

    public OrdenReparacion findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public OrdenReparacion create(OrdenReparacionRequest request) {
        Cliente cliente = clienteService.findById(request.getClienteId());
        Dispositivo dispositivo = dispositivoService.findById(request.getDispositivoId());

        if (dispositivo.getCliente() == null || !dispositivo.getCliente().getId().equals(cliente.getId())) {
            throw new BusinessException("El dispositivo no pertenece al cliente seleccionado");
        }

        OrdenReparacion orden = OrdenReparacion.builder()
                .numeroOrden(generarNumeroOrden())
                .cliente(cliente)
                .dispositivo(dispositivo)
                .problemaReportado(SanitizadorTexto.limpiar(request.getProblemaReportado()))
                .diagnosticoTecnico(SanitizadorTexto.limpiar(request.getDiagnosticoTecnico()))
                .tecnicoResponsable(SanitizadorTexto.limpiar(request.getTecnicoResponsable()))
                .estado(EstadoReparacion.RECIBIDO)
                .costoEstimado(valorOCero(request.getCostoEstimado()))
                .costoFinal(valorOCero(request.getCostoFinal()))
                .fechaEntregaEstimada(request.getFechaEntregaEstimada())
                .recibidoEn(LocalDateTime.now())
                .diasGarantia(request.getDiasGarantia() == null ? 0 : request.getDiasGarantia())
                .nombreFirmaCliente(SanitizadorTexto.limpiar(request.getNombreFirmaCliente()))
                .textoConfirmacion(SanitizadorTexto.limpiar(request.getTextoConfirmacion()))
                .partes(new ArrayList<>())
                .build();

        OrdenReparacion guardada = repository.save(orden);
        adjuntarPartes(guardada, request.getPartes());
        sincronizarCostoFinalConPartes(guardada);
        OrdenReparacion guardadaFinal = repository.save(guardada);

        historyService.logChange(guardadaFinal.getId(), null, guardadaFinal.getEstado().name(), "Orden creada");

        return findById(guardadaFinal.getId());
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public OrdenReparacion updateStatus(Long id, EstadoReparacion estado) {
        OrdenReparacion orden = findById(id);
        String estadoAnterior = orden.getEstado().name();

        orden.setEstado(estado);
        sincronizarCostoFinalConPartes(orden);

        if (estado == EstadoReparacion.ENTREGADO && orden.getEntregadoEn() == null) {
            orden.setEntregadoEn(LocalDateTime.now());
        }

        OrdenReparacion result = repository.save(orden);

        if (estado == EstadoReparacion.ENTREGADO) {
            double montoContable = OrdenMontoUtils.resolveMontoVisible(result);
            if (montoContable > 0) {
                accountingService.saveRepairOrderIncome(result, montoContable);
            }
        }

        historyService.logChange(id, estadoAnterior, estado.name(), "Cambio de estado");
        return result;
    }

    private void adjuntarPartes(OrdenReparacion orden, List<ParteOrdenReparacionRequest> partesRequest) {
        if (partesRequest == null) {
            return;
        }

        for (ParteOrdenReparacionRequest parteRequest : partesRequest) {
            ProductoInventario producto = null;
            ProductoVariante variante = null;
            if (parteRequest.getProductoId() != null) {
                producto = productoInventarioService.findById(parteRequest.getProductoId());
            }
            if (parteRequest.getVarianteId() != null) {
                variante = productoVarianteService.findById(parteRequest.getVarianteId());
            }

            Double costoUnitario = parteRequest.getCostoUnitario();
            Double precioUnitario = parteRequest.getPrecioUnitario();
            Integer cantidad = parteRequest.getCantidad() == null ? 1 : parteRequest.getCantidad();
            TipoFuenteParte tipoFuente = parteRequest.getTipoFuente() == null ? TipoFuenteParte.TIENDA : parteRequest.getTipoFuente();

            if (variante != null && tipoFuente == TipoFuenteParte.TIENDA) {
                costoUnitario = costoUnitario == null ? resolverCostoPromedioConsumoVariante(variante, cantidad, orden.getNumeroOrden()) : costoUnitario;
                precioUnitario = precioUnitario == null ? (variante.getPrecioVentaSugerido() == null ? 0D : variante.getPrecioVentaSugerido()) : precioUnitario;
            }

            ParteOrdenReparacion parte = ParteOrdenReparacion.builder()
                    .ordenReparacion(orden)
                    .producto(producto)
                    .variante(variante)
                    .nombreParte(
                            SanitizadorTexto.limpiar(parteRequest.getNombreParte()) != null
                                    ? SanitizadorTexto.limpiar(parteRequest.getNombreParte())
                                    : resolverNombreParte(producto, variante))
                    .cantidad(cantidad)
                    .costoUnitario(
                            costoUnitario != null
                                    ? costoUnitario
                                    : (producto != null ? producto.getCostoUnitario() : 0D))
                    .precioUnitario(
                            precioUnitario != null
                                    ? precioUnitario
                                    : (producto != null ? producto.getPrecioVenta() : 0D))
                    .tipoFuente(tipoFuente)
                    .notas(SanitizadorTexto.limpiar(parteRequest.getNotas()))
                    .build();

            orden.getPartes().add(parte);

            if (producto != null && parte.getTipoFuente() == TipoFuenteParte.TIENDA) {
                productoInventarioService.adjustStock(
                        producto.getId(),
                        parte.getCantidad(),
                        TipoMovimientoStock.SALIDA,
                        "Salida por orden " + orden.getNumeroOrden(),
                        "ORDEN_REPARACION",
                        orden.getId());
            }
        }
    }

    private String resolverNombreParte(ProductoInventario producto, ProductoVariante variante) {
        if (producto != null) {
            return producto.getNombre();
        }
        if (variante != null && variante.getProductoBase() != null) {
            String calidad = SanitizadorTexto.limpiar(variante.getCalidad());
            if (calidad != null) {
                return variante.getProductoBase().getNombreBase() + " " + calidad;
            }
            return variante.getProductoBase().getNombreBase();
        }
        return "Repuesto";
    }

    private double resolverCostoPromedioConsumoVariante(ProductoVariante variante, int cantidad, String numeroOrden) {
        List<LoteInventario> lotes = loteInventarioRepository.findConsumiblesFifoByVarianteId(variante.getId());
        int restante = cantidad;
        double costoTotal = 0D;

        for (LoteInventario lote : lotes) {
            if (restante <= 0) {
                break;
            }

            int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
            if (disponible <= 0) {
                continue;
            }

            int cantidadTomada = Math.min(disponible, restante);
            lote.setCantidadDisponible(disponible - cantidadTomada);
            actualizarEstadoLoteTrasConsumo(lote);
            loteInventarioRepository.save(lote);
            costoTotal += (lote.getCostoUnitario() == null ? 0D : lote.getCostoUnitario()) * cantidadTomada;
            restante -= cantidadTomada;
        }

        if (restante > 0) {
            throw new BusinessException(
                    "Stock insuficiente para la variante " + variante.getCodigoVariante()
                            + " en la orden " + numeroOrden + ". Faltan " + restante + " unidades.");
        }

        return cantidad <= 0 ? 0D : costoTotal / cantidad;
    }

    private void actualizarEstadoLoteTrasConsumo(LoteInventario lote) {
        int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
        if (disponible <= 0) {
            lote.setCantidadDisponible(0);
            lote.setEstado(EstadoLoteInventario.AGOTADO);
            lote.setVisibleEnVentas(Boolean.FALSE);
            lote.setFechaCierre(LocalDateTime.now());
            return;
        }

        lote.setEstado(EstadoLoteInventario.ACTIVO);
        lote.setActivo(Boolean.TRUE);
        lote.setVisibleEnVentas(Boolean.TRUE);
        lote.setFechaCierre(null);
    }

    private void sincronizarCostoFinalConPartes(OrdenReparacion orden) {
        if (orden == null) {
            return;
        }

        double costoFinalActual = valorOCero(orden.getCostoFinal());
        double montoPartes = OrdenMontoUtils.resolveMontoPartes(orden.getPartes());

        if (costoFinalActual <= 0 && montoPartes > 0) {
            orden.setCostoFinal(montoPartes);
        }
    }

    private String generarNumeroOrden() {
        return "ORD-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Double valorOCero(Double valor) {
        return valor == null ? 0D : valor;
    }
}
