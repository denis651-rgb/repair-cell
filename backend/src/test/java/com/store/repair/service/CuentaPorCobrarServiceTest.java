package com.store.repair.service;

import com.store.repair.domain.AbonoCuentaPorCobrar;
import com.store.repair.domain.Cliente;
import com.store.repair.domain.CuentaPorCobrar;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.EstadoCuentaPorCobrar;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.TipoPagoVenta;
import com.store.repair.domain.Venta;
import com.store.repair.dto.AbonoCuentaPorCobrarRequest;
import com.store.repair.repository.AbonoCuentaPorCobrarRepository;
import com.store.repair.repository.CuentaPorCobrarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CuentaPorCobrarServiceTest {

    private CuentaPorCobrarRepository cuentaRepository;
    private AbonoCuentaPorCobrarRepository abonoRepository;
    private AccountingService accountingService;
    private CuentaPorCobrarService cuentaService;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaPorCobrarRepository.class);
        abonoRepository = mock(AbonoCuentaPorCobrarRepository.class);
        accountingService = mock(AccountingService.class);

        cuentaService = new CuentaPorCobrarService(
                cuentaRepository,
                abonoRepository,
                accountingService);
    }

    @Test
    void registrarAbono_parcial_debeActualizarSaldoEstadoYCrearEntradaContable() {
        CuentaPorCobrar cuenta = crearCuenta(100D, 100D, EstadoCuentaPorCobrar.PENDIENTE);

        AbonoCuentaPorCobrarRequest request = new AbonoCuentaPorCobrarRequest();
        request.setMonto(40D);
        request.setFechaAbono(LocalDate.of(2026, 5, 15));
        request.setObservaciones("Pago por QR");

        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuenta));
        when(abonoRepository.save(any(AbonoCuentaPorCobrar.class))).thenAnswer(invocation -> {
            AbonoCuentaPorCobrar abono = invocation.getArgument(0);
            abono.setId(50L);
            return abono;
        });
        when(cuentaRepository.save(any(CuentaPorCobrar.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountingService.save(any(EntradaContable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CuentaPorCobrar resultado = cuentaService.registrarAbono(1L, request);

        assertEquals(60D, resultado.getSaldoPendiente());
        assertEquals(EstadoCuentaPorCobrar.PARCIAL, resultado.getEstado());

        verify(accountingService).save(argThat(entrada -> entrada.getTipoEntrada() == TipoEntrada.ENTRADA
                && "COBRO_CREDITO".equals(entrada.getCategoria())
                && entrada.getMonto().equals(40D)
                && "ABONO_CUENTA_POR_COBRAR".equals(entrada.getModuloRelacionado())
                && entrada.getRelacionadoId().equals(50L)));
    }

    @Test
    void registrarAbono_total_debeMarcarCuentaComoPagada() {
        CuentaPorCobrar cuenta = crearCuenta(100D, 40D, EstadoCuentaPorCobrar.PARCIAL);

        AbonoCuentaPorCobrarRequest request = new AbonoCuentaPorCobrarRequest();
        request.setMonto(40D);
        request.setFechaAbono(LocalDate.of(2026, 5, 15));
        request.setObservaciones("Pago efectivo");

        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuenta));
        when(abonoRepository.save(any(AbonoCuentaPorCobrar.class))).thenAnswer(invocation -> {
            AbonoCuentaPorCobrar abono = invocation.getArgument(0);
            abono.setId(51L);
            return abono;
        });
        when(cuentaRepository.save(any(CuentaPorCobrar.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountingService.save(any(EntradaContable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CuentaPorCobrar resultado = cuentaService.registrarAbono(1L, request);

        assertEquals(0D, resultado.getSaldoPendiente());
        assertEquals(EstadoCuentaPorCobrar.PAGADA, resultado.getEstado());
    }

    @Test
    void registrarAbono_mayorAlSaldo_debeFallar() {
        CuentaPorCobrar cuenta = crearCuenta(100D, 30D, EstadoCuentaPorCobrar.PARCIAL);

        AbonoCuentaPorCobrarRequest request = new AbonoCuentaPorCobrarRequest();
        request.setMonto(50D);
        request.setFechaAbono(LocalDate.of(2026, 5, 15));
        request.setObservaciones("Pago QR");

        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuenta));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> cuentaService.registrarAbono(1L, request));

        assertEquals("El abono no puede superar el saldo pendiente", ex.getMessage());
    }

    private CuentaPorCobrar crearCuenta(
            Double montoOriginal,
            Double saldoPendiente,
            EstadoCuentaPorCobrar estado) {
        Cliente cliente = Cliente.builder()
                .id(1L)
                .nombreCompleto("Cliente Test")
                .telefono("70000000")
                .build();

        Venta venta = Venta.builder()
                .id(10L)
                .cliente(cliente)
                .fechaVenta(LocalDate.of(2026, 5, 10))
                .numeroComprobante("V-001")
                .tipoPago(TipoPagoVenta.CREDITO)
                .total(montoOriginal)
                .build();

        return CuentaPorCobrar.builder()
                .id(1L)
                .cliente(cliente)
                .venta(venta)
                .fechaEmision(LocalDate.of(2026, 5, 10))
                .montoOriginal(montoOriginal)
                .saldoPendiente(saldoPendiente)
                .estado(estado)
                .build();
    }
}