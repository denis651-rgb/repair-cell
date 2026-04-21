package com.store.repair.controller;

import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.dto.BalanceResponse;
import com.store.repair.service.AccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService service;

    @GetMapping("/entradas")
    public List<EntradaContable> findAll() {
        return service.findAll();
    }

    @GetMapping("/entradas/paginado")
    public Page<EntradaContable> findPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) TipoEntrada tipoEntrada,
            @RequestParam(required = false) String moduloRelacionado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "8") int tamano) {
        return service.findPage(fechaInicio, fechaFin, busqueda, tipoEntrada, moduloRelacionado, pagina, tamano);
    }

    @PostMapping("/entradas")
    public EntradaContable create(@Valid @RequestBody EntradaContable entrada) {
        return service.save(entrada);
    }

    @GetMapping("/balance")
    public BalanceResponse balance(
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return service.getBalance(fechaInicio, fechaFin);
    }
}
