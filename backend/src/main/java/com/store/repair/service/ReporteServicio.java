package com.store.repair.service;

import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.EstadoCuentaPorCobrar;
import com.store.repair.domain.EstadoReparacion;
import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.Venta;
import com.store.repair.dto.ClienteMontoAcumuladoDto;
import com.store.repair.dto.PanelResumenResponse;
import com.store.repair.dto.PanelTallerResponse;
import com.store.repair.dto.ProductoStockBajoResponse;
import com.store.repair.dto.ReporteClienteGlobalResponse;
import com.store.repair.dto.ReporteClienteResponse;
import com.store.repair.dto.ReporteEstadoResponse;
import com.store.repair.dto.ReporteResumenResponse;
import com.store.repair.dto.ResumenGlobalResponse;
import com.store.repair.dto.SerieDiariaResponse;
import com.store.repair.dto.SerieFinancieraDiariaResponse;
import com.store.repair.repository.ClienteRepository;
import com.store.repair.repository.CompraRepository;
import com.store.repair.repository.CuentaPorCobrarRepository;
import com.store.repair.repository.EntradaContableRepository;
import com.store.repair.repository.OrdenReparacionRepository;
import com.store.repair.repository.ProductoInventarioRepository;
import com.store.repair.repository.VentaRepository;
import com.store.repair.repository.AbonoCuentaPorCobrarRepository;
import com.store.repair.util.OrdenMontoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteServicio {

    private final ClienteRepository clienteRepositorio;
    private final OrdenReparacionRepository ordenRepositorio;
    private final ProductoInventarioRepository productoRepositorio;
    private final EntradaContableRepository entradaContableRepositorio;
    private final VentaRepository ventaRepositorio;
    private final CompraRepository compraRepositorio;
    private final CuentaPorCobrarRepository cuentaPorCobrarRepositorio;
    private final AbonoCuentaPorCobrarRepository abonoCuentaPorCobrarRepositorio;

    @Cacheable("reportes_resumen")
    public ReporteResumenResponse obtenerResumen() {
        long totalClientes = clienteRepositorio.count();
        long totalOrdenes = ordenRepositorio.count();
        long ordenesPendientes = ordenRepositorio.countByEstadoNot(EstadoReparacion.ENTREGADO);
        long productosStockBajo = obtenerProductosConStockBajo().size();

        return new ReporteResumenResponse(
                totalClientes,
                totalOrdenes,
                ordenesPendientes,
                productosStockBajo);
    }

    @Cacheable("reportes_panel")
    public PanelTallerResponse obtenerPanelTaller() {
        List<OrdenReparacion> ordenes = ordenRepositorio.findAll();
        List<ProductoInventario> stockBajo = obtenerProductosConStockBajo();

        double totalIngresos = ordenes.stream()
                .mapToDouble(OrdenMontoUtils::resolveMontoVisible)
                .sum();

        return new PanelTallerResponse(
                clienteRepositorio.count(),
                (long) ordenes.size(),
                ordenRepositorio.countByEstadoNot(EstadoReparacion.ENTREGADO),
                (long) stockBajo.size(),
                totalIngresos,
                construirSerieOrdenesPorDia(ordenes),
                construirSerieIngresosOrdenesPorDia(ordenes),
                construirEstadosOrden(ordenes),
                mapearProductosStockBajo(stockBajo));
    }

    @Cacheable("reportes_resumen_global")
    public ResumenGlobalResponse obtenerResumenGlobal() {
        List<EntradaContable> entradasContables = entradaContableRepositorio.findAll();

        double ingresosTotales = sumarPorTipoEntrada(entradasContables, TipoEntrada.ENTRADA);
        double egresosTotales = sumarPorTipoEntrada(entradasContables, TipoEntrada.SALIDA);

        long cuentasAbiertas = cuentaPorCobrarRepositorio.countByEstadoNotAndEstadoNot(
                EstadoCuentaPorCobrar.PAGADA,
                EstadoCuentaPorCobrar.ANULADA);

        double saldoPendiente = valorSeguro(cuentaPorCobrarRepositorio.sumSaldoPendienteByEstadoNotAndEstadoNot(
                EstadoCuentaPorCobrar.PAGADA,
                EstadoCuentaPorCobrar.ANULADA));

        return new ResumenGlobalResponse(
                clienteRepositorio.count(),
                ordenRepositorio.count(),
                ordenRepositorio.countByEstadoNot(EstadoReparacion.ENTREGADO),
                ventaRepositorio.count(),
                compraRepositorio.count(),
                cuentasAbiertas,
                saldoPendiente,
                obtenerProductosConStockBajo().size(),
                ingresosTotales,
                egresosTotales,
                ingresosTotales - egresosTotales);
    }

    @Cacheable("reportes_panel_global")
    public PanelResumenResponse obtenerPanelGlobal() {
        List<OrdenReparacion> ordenes = ordenRepositorio.findAll();
        List<EntradaContable> entradasContables = entradaContableRepositorio.findAll();
        List<ProductoInventario> stockBajo = obtenerProductosConStockBajo();

        double ingresosTotales = sumarPorTipoEntrada(entradasContables, TipoEntrada.ENTRADA);
        double egresosTotales = sumarPorTipoEntrada(entradasContables, TipoEntrada.SALIDA);
        double ingresosReparaciones = sumarPorModulo(entradasContables, TipoEntrada.ENTRADA, "ORDEN_REPARACION");
        double ingresosVentas = sumarPorModulo(entradasContables, TipoEntrada.ENTRADA, "VENTA");
        double cobrosCredito = sumarPorModulo(entradasContables, TipoEntrada.ENTRADA, "ABONO_CUENTA_POR_COBRAR");
        double egresosCompras = sumarPorModulo(entradasContables, TipoEntrada.SALIDA, "COMPRA");
        double egresosManuales = entradasContables.stream()
                .filter(entrada -> entrada.getTipoEntrada() == TipoEntrada.SALIDA)
                .filter(entrada -> entrada.getModuloRelacionado() == null || entrada.getModuloRelacionado().isBlank())
                .mapToDouble(entrada -> valorSeguro(entrada.getMonto()))
                .sum();

        long cuentasAbiertas = cuentaPorCobrarRepositorio.countByEstadoNotAndEstadoNot(
                EstadoCuentaPorCobrar.PAGADA,
                EstadoCuentaPorCobrar.ANULADA);

        double saldoPendiente = valorSeguro(cuentaPorCobrarRepositorio.sumSaldoPendienteByEstadoNotAndEstadoNot(
                EstadoCuentaPorCobrar.PAGADA,
                EstadoCuentaPorCobrar.ANULADA));

        return new PanelResumenResponse(
                clienteRepositorio.count(),
                (long) ordenes.size(),
                ordenRepositorio.countByEstadoNot(EstadoReparacion.ENTREGADO),
                ventaRepositorio.count(),
                compraRepositorio.count(),
                cuentasAbiertas,
                saldoPendiente,
                (long) stockBajo.size(),
                ingresosTotales,
                egresosTotales,
                ingresosTotales - egresosTotales,
                construirSerieOperacionesPorDia(entradasContables),
                construirSerieMontoPorDia(entradasContables, TipoEntrada.ENTRADA),
                construirSerieMontoPorDia(entradasContables, TipoEntrada.SALIDA),
                construirEstadosOrden(ordenes),
                mapearProductosStockBajo(stockBajo),
                ingresosReparaciones,
                ingresosVentas,
                cobrosCredito,
                egresosCompras,
                egresosManuales);
    }

    public List<SerieDiariaResponse> obtenerReportePorFecha(LocalDate inicio, LocalDate fin) {
        LocalDate fechaInicio = resolverFechaInicio(inicio);
        LocalDate fechaFin = resolverFechaFin(fin);
        validarRangoFechas(fechaInicio, fechaFin);

        List<OrdenReparacion> ordenes = ordenRepositorio.findByRecibidoEnBetweenOrderByRecibidoEnAsc(
                fechaInicio.atStartOfDay(),
                fechaFin.plusDays(1).atStartOfDay().minusSeconds(1));

        Map<LocalDate, Double> acumulado = inicializarSerieMontos(fechaInicio, fechaFin);

        for (OrdenReparacion orden : ordenes) {
            if (orden.getRecibidoEn() == null) {
                continue;
            }

            LocalDate fecha = orden.getRecibidoEn().toLocalDate();
            acumulado.computeIfPresent(
                    fecha,
                    (clave, valorActual) -> valorActual + OrdenMontoUtils.resolveMontoVisible(orden));
        }

        return acumulado.entrySet().stream()
                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    public List<SerieFinancieraDiariaResponse> obtenerFinancieroPorFecha(LocalDate inicio, LocalDate fin) {
        LocalDate fechaInicio = resolverFechaInicio(inicio);
        LocalDate fechaFin = resolverFechaFin(fin);
        validarRangoFechas(fechaInicio, fechaFin);

        List<EntradaContable> entradas = entradaContableRepositorio
                .findByFechaEntradaBetweenOrderByFechaEntradaDesc(fechaInicio, fechaFin);

        Map<LocalDate, AcumuladorFinancieroDiario> acumulado = new LinkedHashMap<>();
        LocalDate cursor = fechaInicio;
        while (!cursor.isAfter(fechaFin)) {
            acumulado.put(cursor, new AcumuladorFinancieroDiario());
            cursor = cursor.plusDays(1);
        }

        for (EntradaContable entrada : entradas) {
            if (entrada.getFechaEntrada() == null) {
                continue;
            }

            AcumuladorFinancieroDiario dia = acumulado.get(entrada.getFechaEntrada());
            if (dia == null) {
                continue;
            }

            double monto = valorSeguro(entrada.getMonto());
            if (entrada.getTipoEntrada() == TipoEntrada.ENTRADA) {
                dia.ingresos += monto;
            } else if (entrada.getTipoEntrada() == TipoEntrada.SALIDA) {
                dia.egresos += monto;
            }

            String modulo = entrada.getModuloRelacionado() == null ? "" : entrada.getModuloRelacionado().trim();
            if ("VENTA".equalsIgnoreCase(modulo)) {
                dia.ventas += monto;
            } else if ("COMPRA".equalsIgnoreCase(modulo)) {
                dia.compras += monto;
            } else if ("ORDEN_REPARACION".equalsIgnoreCase(modulo)) {
                dia.reparaciones += monto;
            }
        }

        return acumulado.entrySet().stream()
                .map(entry -> new SerieFinancieraDiariaResponse(
                        entry.getKey().toString(),
                        entry.getValue().ingresos,
                        entry.getValue().egresos,
                        entry.getValue().ingresos - entry.getValue().egresos,
                        entry.getValue().ventas,
                        entry.getValue().compras,
                        entry.getValue().reparaciones))
                .toList();
    }

    public List<ReporteClienteResponse> obtenerReportePorCliente() {
        return ordenRepositorio.findAll().stream()
                .filter(orden -> orden.getCliente() != null)
                .collect(Collectors.groupingBy(
                        orden -> orden.getCliente().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .map(lista -> new ReporteClienteResponse(
                        lista.get(0).getCliente().getId(),
                        lista.get(0).getCliente().getNombreCompleto(),
                        lista.size(),
                        lista.stream().mapToDouble(OrdenMontoUtils::resolveMontoVisible).sum()))
                .sorted(Comparator.comparingDouble(ReporteClienteResponse::totalFacturado).reversed())
                .toList();
    }

    @Cacheable("reportes_clientes_global")
    public List<ReporteClienteGlobalResponse> obtenerClientesGlobal() {
        Map<Long, ReporteClienteGlobalBuilder> acumulado = new LinkedHashMap<>();

        for (OrdenReparacion orden : ordenRepositorio.findAll()) {
            if (orden.getCliente() == null) {
                continue;
            }

            ReporteClienteGlobalBuilder builder = acumulado.computeIfAbsent(
                    orden.getCliente().getId(),
                    clienteId -> new ReporteClienteGlobalBuilder(clienteId, orden.getCliente().getNombreCompleto()));
            builder.totalOrdenes += 1;
            builder.totalReparaciones += OrdenMontoUtils.resolveMontoVisible(orden);
        }

        for (Venta venta : ventaRepositorio.findAll()) {
            if (venta.getCliente() == null) {
                continue;
            }

            ReporteClienteGlobalBuilder builder = acumulado.computeIfAbsent(
                    venta.getCliente().getId(),
                    clienteId -> new ReporteClienteGlobalBuilder(clienteId, venta.getCliente().getNombreCompleto()));
            builder.totalVentas += valorSeguro(venta.getTotal());
        }

        for (ClienteMontoAcumuladoDto saldoPendiente : cuentaPorCobrarRepositorio.sumarSaldoPendientePorCliente()) {
            if (saldoPendiente.clienteId() == null) {
                continue;
            }

            ReporteClienteGlobalBuilder builder = acumulado.computeIfAbsent(
                    saldoPendiente.clienteId(),
                    clienteId -> new ReporteClienteGlobalBuilder(clienteId, saldoPendiente.cliente()));
            builder.saldoPendiente += valorSeguro(saldoPendiente.monto());
        }

        for (ClienteMontoAcumuladoDto abono : abonoCuentaPorCobrarRepositorio.sumarAbonosPorCliente()) {
            if (abono.clienteId() == null) {
                continue;
            }

            ReporteClienteGlobalBuilder builder = acumulado.computeIfAbsent(
                    abono.clienteId(),
                    clienteId -> new ReporteClienteGlobalBuilder(clienteId, abono.cliente()));
            builder.totalAbonado += valorSeguro(abono.monto());
        }

        return acumulado.values().stream()
                .map(builder -> new ReporteClienteGlobalResponse(
                        builder.clienteId,
                        builder.cliente,
                        builder.totalOrdenes,
                        builder.totalReparaciones,
                        builder.totalVentas,
                        builder.totalReparaciones + builder.totalVentas,
                        builder.saldoPendiente,
                        builder.totalAbonado))
                .sorted(Comparator.comparingDouble(ReporteClienteGlobalResponse::totalConsumidoGlobal).reversed())
                .toList();
    }

    private List<ProductoInventario> obtenerProductosConStockBajo() {
        return productoRepositorio.findByCantidadStockLessThanEqualOrderByCantidadStockAsc(Integer.MAX_VALUE)
                .stream()
                .filter(producto -> producto.getCantidadStock() != null)
                .filter(producto -> producto.getStockMinimo() != null)
                .filter(producto -> producto.getCantidadStock() <= producto.getStockMinimo())
                .toList();
    }

    private List<ProductoStockBajoResponse> mapearProductosStockBajo(List<ProductoInventario> productos) {
        return productos.stream()
                .limit(10)
                .map(producto -> new ProductoStockBajoResponse(
                        producto.getId(),
                        producto.getNombre(),
                        producto.getCantidadStock(),
                        producto.getStockMinimo()))
                .toList();
    }

    private List<SerieDiariaResponse> construirSerieOrdenesPorDia(List<OrdenReparacion> ordenes) {
        Map<LocalDate, Long> agrupado = ordenes.stream()
                .filter(orden -> orden.getRecibidoEn() != null)
                .collect(Collectors.groupingBy(
                        orden -> orden.getRecibidoEn().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()));

        return agrupado.entrySet().stream()
                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<SerieDiariaResponse> construirSerieIngresosOrdenesPorDia(List<OrdenReparacion> ordenes) {
        Map<LocalDate, Double> agrupado = ordenes.stream()
                .filter(orden -> orden.getRecibidoEn() != null)
                .collect(Collectors.groupingBy(
                        orden -> orden.getRecibidoEn().toLocalDate(),
                        TreeMap::new,
                        Collectors.summingDouble(OrdenMontoUtils::resolveMontoVisible)));

        return agrupado.entrySet().stream()
                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<SerieDiariaResponse> construirSerieOperacionesPorDia(List<EntradaContable> entradas) {
        Map<LocalDate, Long> agrupado = entradas.stream()
                .filter(entrada -> entrada.getFechaEntrada() != null)
                .collect(Collectors.groupingBy(
                        EntradaContable::getFechaEntrada,
                        TreeMap::new,
                        Collectors.counting()));

        return agrupado.entrySet().stream()
                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<SerieDiariaResponse> construirSerieMontoPorDia(List<EntradaContable> entradas, TipoEntrada tipoEntrada) {
        Map<LocalDate, Double> agrupado = entradas.stream()
                .filter(entrada -> entrada.getFechaEntrada() != null)
                .filter(entrada -> entrada.getTipoEntrada() == tipoEntrada)
                .collect(Collectors.groupingBy(
                        EntradaContable::getFechaEntrada,
                        TreeMap::new,
                        Collectors.summingDouble(entrada -> valorSeguro(entrada.getMonto()))));

        return agrupado.entrySet().stream()
                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<ReporteEstadoResponse> construirEstadosOrden(List<OrdenReparacion> ordenes) {
        return ordenes.stream()
                .collect(Collectors.groupingBy(
                        orden -> String.valueOf(orden.getEstado()),
                        LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new ReporteEstadoResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<LocalDate, Double> inicializarSerieMontos(LocalDate inicio, LocalDate fin) {
        Map<LocalDate, Double> acumulado = new LinkedHashMap<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fin)) {
            acumulado.put(cursor, 0D);
            cursor = cursor.plusDays(1);
        }
        return acumulado;
    }

    private double sumarPorTipoEntrada(List<EntradaContable> entradas, TipoEntrada tipoEntrada) {
        return entradas.stream()
                .filter(entrada -> entrada.getTipoEntrada() == tipoEntrada)
                .mapToDouble(entrada -> valorSeguro(entrada.getMonto()))
                .sum();
    }

    private double sumarPorModulo(List<EntradaContable> entradas, TipoEntrada tipoEntrada, String moduloRelacionado) {
        return entradas.stream()
                .filter(entrada -> entrada.getTipoEntrada() == tipoEntrada)
                .filter(entrada -> moduloRelacionado.equalsIgnoreCase(
                        entrada.getModuloRelacionado() == null ? "" : entrada.getModuloRelacionado().trim()))
                .mapToDouble(entrada -> valorSeguro(entrada.getMonto()))
                .sum();
    }

    private LocalDate resolverFechaInicio(LocalDate inicio) {
        return inicio == null ? LocalDate.now().minusDays(6) : inicio;
    }

    private LocalDate resolverFechaFin(LocalDate fin) {
        return fin == null ? LocalDate.now() : fin;
    }

    private void validarRangoFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaFin.isBefore(fechaInicio)) {
            throw new BusinessException("La fecha fin no puede ser menor a la fecha inicio");
        }
    }

    private double valorSeguro(Double valor) {
        return valor == null ? 0D : valor;
    }

    private static class AcumuladorFinancieroDiario {
        private double ingresos;
        private double egresos;
        private double ventas;
        private double compras;
        private double reparaciones;
    }

    private static class ReporteClienteGlobalBuilder {
        private final Long clienteId;
        private final String cliente;
        private long totalOrdenes;
        private double totalReparaciones;
        private double totalVentas;
        private double saldoPendiente;
        private double totalAbonado;

        private ReporteClienteGlobalBuilder(Long clienteId, String cliente) {
            this.clienteId = clienteId;
            this.cliente = cliente;
        }
    }
}
