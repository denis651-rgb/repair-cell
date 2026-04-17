package com.store.repair.controller;

import com.store.repair.dto.PanelResumenResponse;
import com.store.repair.dto.ReporteClienteResponse;
import com.store.repair.dto.ReporteResumenResponse;
import com.store.repair.dto.ReporteTecnicoResponse;
import com.store.repair.dto.SerieDiariaResponse;
import com.store.repair.service.ReporteServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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
    public PanelResumenResponse obtenerPanel() {
        return reporteServicio.obtenerPanelResumen();
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
    public List<ReporteTecnicoResponse> obtenerPorTecnico() {
        return reporteServicio.obtenerReportePorTecnico();
    }
}