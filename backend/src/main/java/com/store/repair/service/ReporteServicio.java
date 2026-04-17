package com.store.repair.service;

import com.store.repair.domain.EstadoReparacion;
import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.ProductoInventario;
import com.store.repair.dto.PanelResumenResponse;
import com.store.repair.dto.ProductoStockBajoResponse;
import com.store.repair.dto.ReporteClienteResponse;
import com.store.repair.dto.ReporteEstadoResponse;
import com.store.repair.dto.ReporteResumenResponse;
import com.store.repair.dto.ReporteTecnicoResponse;
import com.store.repair.dto.SerieDiariaResponse;
import com.store.repair.repository.ClienteRepository;
import com.store.repair.repository.OrdenReparacionRepository;
import com.store.repair.repository.ProductoInventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteServicio {

        private final ClienteRepository clienteRepositorio;
        private final OrdenReparacionRepository ordenRepositorio;
        private final ProductoInventarioRepository productoRepositorio;

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
        public PanelResumenResponse obtenerPanelResumen() {
                List<OrdenReparacion> ordenes = ordenRepositorio.findAll();
                List<ProductoInventario> stockBajo = obtenerProductosConStockBajo();

                double totalIngresos = ordenes.stream()
                                .mapToDouble(orden -> orden.getCostoFinal() == null ? 0D : orden.getCostoFinal())
                                .sum();

                return new PanelResumenResponse(
                                clienteRepositorio.count(),
                                (long) ordenes.size(),
                                ordenRepositorio.countByEstadoNot(EstadoReparacion.ENTREGADO),
                                (long) stockBajo.size(),
                                totalIngresos,
                                construirSerieOrdenesPorDia(ordenes),
                                construirSerieIngresosPorDia(ordenes),
                                construirEstados(ordenes),
                                stockBajo.stream()
                                                .limit(10)
                                                .map(producto -> new ProductoStockBajoResponse(
                                                                producto.getId(),
                                                                producto.getNombre(),
                                                                producto.getCantidadStock(),
                                                                producto.getStockMinimo()))
                                                .toList());
        }

        public List<SerieDiariaResponse> obtenerReportePorFecha(LocalDate inicio, LocalDate fin) {
                LocalDate fechaInicio = inicio == null ? LocalDate.now().minusDays(6) : inicio;
                LocalDate fechaFin = fin == null ? LocalDate.now() : fin;

                if (fechaFin.isBefore(fechaInicio)) {
                        throw new BusinessException("La fecha fin no puede ser menor a la fecha inicio");
                }

                List<OrdenReparacion> ordenes = ordenRepositorio.findByRecibidoEnBetweenOrderByRecibidoEnAsc(
                                fechaInicio.atStartOfDay(),
                                fechaFin.plusDays(1).atStartOfDay().minusSeconds(1));

                Map<LocalDate, Double> acumulado = new LinkedHashMap<>();
                LocalDate cursor = fechaInicio;

                while (!cursor.isAfter(fechaFin)) {
                        acumulado.put(cursor, 0D);
                        cursor = cursor.plusDays(1);
                }

                for (OrdenReparacion orden : ordenes) {
                        if (orden.getRecibidoEn() == null) {
                                continue;
                        }

                        LocalDate fecha = orden.getRecibidoEn().toLocalDate();
                        acumulado.computeIfPresent(
                                        fecha,
                                        (clave, valorActual) -> valorActual
                                                        + (orden.getCostoFinal() == null ? 0D : orden.getCostoFinal()));
                }

                return acumulado.entrySet().stream()
                                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
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
                                                lista.stream()
                                                                .mapToDouble(orden -> orden.getCostoFinal() == null ? 0D
                                                                                : orden.getCostoFinal())
                                                                .sum()))
                                .sorted(Comparator.comparingDouble(ReporteClienteResponse::totalFacturado).reversed())
                                .toList();
        }

        public List<ReporteTecnicoResponse> obtenerReportePorTecnico() {
                return ordenRepositorio.findAll().stream()
                                .collect(Collectors.groupingBy(
                                                orden -> Optional.ofNullable(orden.getTecnicoResponsable())
                                                                .filter(valor -> !valor.isBlank())
                                                                .orElse("SIN ASIGNAR"),
                                                LinkedHashMap::new,
                                                Collectors.toList()))
                                .entrySet().stream()
                                .map(entry -> new ReporteTecnicoResponse(
                                                entry.getKey(),
                                                entry.getValue().size(),
                                                entry.getValue().stream()
                                                                .mapToDouble(orden -> orden.getCostoFinal() == null ? 0D
                                                                                : orden.getCostoFinal())
                                                                .sum()))
                                .sorted(Comparator.comparingDouble(ReporteTecnicoResponse::totalFacturado).reversed())
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

        private List<SerieDiariaResponse> construirSerieIngresosPorDia(List<OrdenReparacion> ordenes) {
                Map<LocalDate, Double> agrupado = ordenes.stream()
                                .filter(orden -> orden.getRecibidoEn() != null)
                                .collect(Collectors.groupingBy(
                                                orden -> orden.getRecibidoEn().toLocalDate(),
                                                TreeMap::new,
                                                Collectors.summingDouble(orden -> orden.getCostoFinal() == null ? 0D
                                                                : orden.getCostoFinal())));

                return agrupado.entrySet().stream()
                                .map(entry -> new SerieDiariaResponse(entry.getKey().toString(), entry.getValue()))
                                .toList();
        }

        private List<ReporteEstadoResponse> construirEstados(List<OrdenReparacion> ordenes) {
                return ordenes.stream()
                                .collect(Collectors.groupingBy(
                                                orden -> String.valueOf(orden.getEstado()),
                                                LinkedHashMap::new,
                                                Collectors.counting()))
                                .entrySet().stream()
                                .map(entry -> new ReporteEstadoResponse(entry.getKey(), entry.getValue()))
                                .toList();
        }
}