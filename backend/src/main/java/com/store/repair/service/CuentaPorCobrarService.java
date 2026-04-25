package com.store.repair.service;

import com.store.repair.config.SanitizadorTexto;
import com.store.repair.domain.*;
import com.store.repair.dto.AbonoCuentaPorCobrarRequest;
import com.store.repair.repository.AbonoCuentaPorCobrarRepository;
import com.store.repair.repository.CuentaPorCobrarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CuentaPorCobrarService {

    private final CuentaPorCobrarRepository repository;
    private final AbonoCuentaPorCobrarRepository abonoRepository;
    private final AccountingService accountingService;

    public Page<CuentaPorCobrar> findPage(String busqueda, EstadoCuentaPorCobrar estado, int pagina, int tamano) {
        Page<CuentaPorCobrar> page = repository.search(
                busqueda == null ? "" : busqueda.trim(),
                estado,
                PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1)));
        return new PageImpl<>(
                page.getContent().stream().map(cuenta -> findById(cuenta.getId())).toList(),
                page.getPageable(),
                page.getTotalElements());
    }

    public CuentaPorCobrar findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada: " + id));
    }

    @Transactional
    @CacheEvict(value = {
            "reportes_resumen",
            "reportes_panel",
            "reportes_resumen_global",
            "reportes_panel_global",
            "reportes_clientes_global"
    }, allEntries = true)
    public CuentaPorCobrar registrarAbono(Long cuentaId, AbonoCuentaPorCobrarRequest request) {
        CuentaPorCobrar cuenta = findById(cuentaId);

        if (cuenta.getEstado() == EstadoCuentaPorCobrar.PAGADA) {
            throw new BusinessException("La cuenta por cobrar ya esta pagada");
        }

        if (request.getMonto() == null || request.getMonto() <= 0) {
            throw new BusinessException("El abono debe ser mayor a cero");
        }

        if (request.getMonto() > cuenta.getSaldoPendiente()) {
            throw new BusinessException("El abono no puede superar el saldo pendiente");
        }

        AbonoCuentaPorCobrar abono = AbonoCuentaPorCobrar.builder()
                .cuentaPorCobrar(cuenta)
                .fechaAbono(request.getFechaAbono() == null ? LocalDate.now() : request.getFechaAbono())
                .monto(request.getMonto())
                .observaciones(SanitizadorTexto.limpiar(request.getObservaciones()))
                .build();

        abono = abonoRepository.save(abono);

        double nuevoSaldo = cuenta.getSaldoPendiente() - request.getMonto();
        cuenta.setSaldoPendiente(nuevoSaldo);
        if (nuevoSaldo <= 0) {
            cuenta.setSaldoPendiente(0D);
            cuenta.setEstado(EstadoCuentaPorCobrar.PAGADA);
        } else if (nuevoSaldo < cuenta.getMontoOriginal()) {
            cuenta.setEstado(EstadoCuentaPorCobrar.PARCIAL);
        }

        registrarEntradaPorAbono(cuenta, abono);
        return repository.save(cuenta);
    }

    private void registrarEntradaPorAbono(CuentaPorCobrar cuenta, AbonoCuentaPorCobrar abono) {
        EntradaContable entrada = EntradaContable.builder()
                .tipoEntrada(TipoEntrada.ENTRADA)
                .categoria("COBRO_CREDITO")
                .descripcion("Abono de venta " + referenciaVenta(cuenta.getVenta()) + " de "
                        + cuenta.getCliente().getNombreCompleto())
                .monto(abono.getMonto())
                .fechaEntrada(abono.getFechaAbono())
                .moduloRelacionado("ABONO_CUENTA_POR_COBRAR")
                .relacionadoId(abono.getId())
                .build();

        accountingService.save(entrada);
    }

    private String referenciaVenta(Venta venta) {
        return venta.getNumeroComprobante() != null ? venta.getNumeroComprobante() : ("#" + venta.getId());
    }
}
