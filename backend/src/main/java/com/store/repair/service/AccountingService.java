package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.CajaDiaria;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.dto.BalanceResponse;
import com.store.repair.dto.CajaResumenActualResponse;
import com.store.repair.repository.CajaDiariaRepository;
import com.store.repair.repository.EntradaContableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountingService {

    private final EntradaContableRepository repository;
    private final CajaDiariaRepository cajaRepository;

    public List<EntradaContable> findAll() {
        return repository.findAllByOrderByFechaEntradaDescIdDesc();
    }

    public Page<EntradaContable> findPage(LocalDate fechaInicio, LocalDate fechaFin, int pagina, int tamano) {
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));

        if (fechaInicio != null && fechaFin != null) {
            if (fechaFin.isBefore(fechaInicio)) {
                throw new BusinessException("La fecha fin no puede ser menor a la fecha inicio");
            }
            return repository.findByFechaEntradaBetweenOrderByFechaEntradaDesc(fechaInicio, fechaFin, pageRequest);
        }

        return repository.findAllByOrderByFechaEntradaDescIdDesc(pageRequest);
    }

    @Transactional
    public EntradaContable save(EntradaContable entry) {
        if (entry.getMonto() == null || entry.getMonto() < 0) {
            throw new BusinessException("El monto no puede ser negativo");
        }

        if (entry.getFechaEntrada() == null) {
            entry.setFechaEntrada(LocalDate.now());
        }

        entry.setCategoria(SanitizadorTexto.limpiar(entry.getCategoria()));
        entry.setDescripcion(SanitizadorTexto.limpiar(entry.getDescripcion()));
        entry.setModuloRelacionado(SanitizadorTexto.limpiar(entry.getModuloRelacionado()));

        cajaRepository.findByEstado("ABIERTA").ifPresent(caja -> entry.setCajaId(caja.getId()));

        return repository.save(entry);
    }

    @Transactional
    public EntradaContable saveRepairOrderIncome(OrdenReparacion orden, double monto) {
        if (orden == null || orden.getId() == null) {
            throw new BusinessException("La orden relacionada es obligatoria");
        }

        if (monto <= 0) {
            throw new BusinessException("El monto de la orden entregada debe ser mayor a cero");
        }

        EntradaContable entry = repository
                .findFirstByModuloRelacionadoAndRelacionadoId("ORDEN_REPARACION", orden.getId())
                .orElseGet(() -> EntradaContable.builder()
                        .moduloRelacionado("ORDEN_REPARACION")
                        .relacionadoId(orden.getId())
                        .tipoEntrada(TipoEntrada.ENTRADA)
                        .build());

        entry.setCategoria("REPARACION");
        entry.setDescripcion("Cobro de orden " + orden.getNumeroOrden());
        entry.setMonto(monto);
        entry.setFechaEntrada(LocalDate.now());

        return save(entry);
    }

    @Transactional
    public CajaDiaria abrirCaja(Double montoApertura, String usuario) {
        if (montoApertura == null || montoApertura < 0) {
            throw new BusinessException("El monto de apertura no puede ser negativo");
        }

        if (usuario == null || usuario.isBlank()) {
            throw new BusinessException("El usuario de apertura es obligatorio");
        }

        if (cajaRepository.findByEstado("ABIERTA").isPresent()) {
            throw new BusinessException("Ya existe una caja abierta");
        }

        CajaDiaria caja = CajaDiaria.builder()
                .fechaApertura(java.time.LocalDateTime.now())
                .montoApertura(montoApertura)
                .estado("ABIERTA")
                .usuarioApertura(SanitizadorTexto.limpiar(usuario))
                .build();

        return cajaRepository.save(caja);
    }

    @Transactional
    public CajaDiaria cerrarCaja(Long id, Double montoCierre, String usuario, String observaciones) {
        if (id == null) {
            throw new BusinessException("El id de caja es obligatorio");
        }

        if (montoCierre == null || montoCierre < 0) {
            throw new BusinessException("El monto de cierre no puede ser negativo");
        }

        if (usuario == null || usuario.isBlank()) {
            throw new BusinessException("El usuario de cierre es obligatorio");
        }

        CajaDiaria caja = cajaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caja no encontrada"));

        if (!"ABIERTA".equalsIgnoreCase(caja.getEstado())) {
            throw new BusinessException("La caja indicada no está abierta");
        }

        List<EntradaContable> entradas = repository.findByCajaId(id);

        double totalEntradas = entradas.stream()
                .filter(e -> e.getTipoEntrada() == TipoEntrada.ENTRADA)
                .mapToDouble(EntradaContable::getMonto)
                .sum();

        double totalSalidas = entradas.stream()
                .filter(e -> e.getTipoEntrada() == TipoEntrada.SALIDA)
                .mapToDouble(EntradaContable::getMonto)
                .sum();

        caja.setMontoEsperado(caja.getMontoApertura() + totalEntradas - totalSalidas);
        caja.setMontoCierre(montoCierre);
        caja.setFechaCierre(java.time.LocalDateTime.now());
        caja.setEstado("CERRADA");
        caja.setUsuarioCierre(SanitizadorTexto.limpiar(usuario));
        caja.setObservaciones(SanitizadorTexto.limpiar(observaciones));

        return cajaRepository.save(caja);
    }

    public CajaDiaria getCajaActual() {
        return cajaRepository.findByEstado("ABIERTA").orElse(null);
    }

    public CajaResumenActualResponse getCajaResumenActual() {
        CajaDiaria caja = getCajaActual();
        if (caja == null) {
            return new CajaResumenActualResponse(0, 0, 0, 0);
        }

        List<EntradaContable> entradas = repository.findByCajaId(caja.getId());

        double totalEntradas = entradas.stream()
                .filter(entry -> entry.getTipoEntrada() == TipoEntrada.ENTRADA)
                .mapToDouble(entry -> entry.getMonto() == null ? 0 : entry.getMonto())
                .sum();

        double totalSalidas = entradas.stream()
                .filter(entry -> entry.getTipoEntrada() == TipoEntrada.SALIDA)
                .mapToDouble(entry -> entry.getMonto() == null ? 0 : entry.getMonto())
                .sum();

        double esperado = (caja.getMontoApertura() == null ? 0 : caja.getMontoApertura()) + totalEntradas - totalSalidas;

        return new CajaResumenActualResponse(totalEntradas, totalSalidas, entradas.size(), esperado);
    }

    public BalanceResponse getBalance(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("Las fechas de inicio y fin son obligatorias");
        }

        if (endDate.isBefore(startDate)) {
            throw new BusinessException("La fecha fin no puede ser menor a la fecha inicio");
        }

        List<EntradaContable> entries = repository.findByFechaEntradaBetweenOrderByFechaEntradaDesc(startDate, endDate);

        double entradas = entries.stream()
                .filter(entry -> entry.getTipoEntrada() == TipoEntrada.ENTRADA)
                .mapToDouble(EntradaContable::getMonto)
                .sum();

        double salidas = entries.stream()
                .filter(entry -> entry.getTipoEntrada() == TipoEntrada.SALIDA)
                .mapToDouble(EntradaContable::getMonto)
                .sum();

        return new BalanceResponse(entradas, salidas, entradas - salidas);
    }
}
