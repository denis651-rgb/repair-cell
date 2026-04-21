package com.store.repair.controller;

import com.store.repair.dto.PanelResumenResponse;
import com.store.repair.dto.PanelTallerResponse;
import com.store.repair.dto.ReporteClienteGlobalResponse;
import com.store.repair.dto.ReporteClienteResponse;
import com.store.repair.dto.ReporteResumenResponse;
import com.store.repair.dto.ResumenGlobalResponse;
import com.store.repair.dto.SerieDiariaResponse;
import com.store.repair.dto.SerieFinancieraDiariaResponse;
import com.store.repair.service.ReporteServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteControlador {

    private final ReporteServicio reporteServicio;

    @GetMapping("/resumen")
    public ReporteResumenResponse obtenerResumen() {
        return reporteServicio.obtenerResumen();
    }

    @GetMapping("/panel")
    public PanelTallerResponse obtenerPanel() {
        return reporteServicio.obtenerPanelTaller();
    }

    @GetMapping("/por-fecha")
    public List<SerieDiariaResponse> obtenerPorFecha(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return reporteServicio.obtenerReportePorFecha(inicio, fin);
    }

    @GetMapping("/por-cliente")
    public List<ReporteClienteResponse> obtenerPorCliente() {
        return reporteServicio.obtenerReportePorCliente();
    }

    @GetMapping("/por-tecnico")
    public List<Map<String, Object>> obtenerPorTecnico() {
        return List.of();
    }

    @GetMapping("/resumen-global")
    public ResumenGlobalResponse obtenerResumenGlobal() {
        return reporteServicio.obtenerResumenGlobal();
    }

    @GetMapping("/panel-global")
    public PanelResumenResponse obtenerPanelGlobal() {
        return reporteServicio.obtenerPanelGlobal();
    }

    @GetMapping("/financiero-por-fecha")
    public List<SerieFinancieraDiariaResponse> obtenerFinancieroPorFecha(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return reporteServicio.obtenerFinancieroPorFecha(inicio, fin);
    }

    @GetMapping("/clientes-global")
    public List<ReporteClienteGlobalResponse> obtenerClientesGlobal() {
        return reporteServicio.obtenerClientesGlobal();
    }
}
