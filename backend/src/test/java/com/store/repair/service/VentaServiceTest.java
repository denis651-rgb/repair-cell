package com.store.repair.service;

import com.store.repair.domain.CategoriaInventario;
import com.store.repair.domain.Cliente;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.domain.ProductoBase;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.TipoPagoVenta;
import com.store.repair.domain.Venta;
import com.store.repair.dto.VentaDetalleRegistroRequest;
import com.store.repair.dto.VentaRegistroRequest;
import com.store.repair.repository.CuentaPorCobrarRepository;
import com.store.repair.repository.DevolucionVentaRepository;
import com.store.repair.repository.EntradaContableRepository;
import com.store.repair.repository.LoteInventarioRepository;
import com.store.repair.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VentaServiceTest {

    private VentaRepository ventaRepository;
    private ClienteService clienteService;
    private ProductoVarianteService varianteService;
    private LoteInventarioRepository loteRepository;
    private AccountingService accountingService;
    private EntradaContableRepository entradaContableRepository;
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    private DevolucionVentaRepository devolucionVentaRepository;
    private ComprobanteService comprobanteService;
    private VentaService ventaService;

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        clienteService = mock(ClienteService.class);
        varianteService = mock(ProductoVarianteService.class);
        loteRepository = mock(LoteInventarioRepository.class);
        accountingService = mock(AccountingService.class);
        entradaContableRepository = mock(EntradaContableRepository.class);
        cuentaPorCobrarRepository = mock(CuentaPorCobrarRepository.class);
        devolucionVentaRepository = mock(DevolucionVentaRepository.class);
        comprobanteService = mock(ComprobanteService.class);

        ventaService = new VentaService(
                ventaRepository,
                clienteService,
                varianteService,
                loteRepository,
                accountingService,
                entradaContableRepository,
                cuentaPorCobrarRepository,
                devolucionVentaRepository,
                comprobanteService);
    }

    @Test
    void registrarVenta_sinDetalles_debeFallar() {
        VentaRegistroRequest request = new VentaRegistroRequest();
        request.setClienteId(1L);
        request.setTipoPago(TipoPagoVenta.CONTADO);
        request.setDetalles(List.of());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ventaService.registrarVenta(request));

        assertEquals("La venta debe incluir al menos una variante", ex.getMessage());
    }

    @Test
    void registrarVenta_contadoConLote_debeDescontarStockYCrearEntradaContable() {
        Cliente cliente = Cliente.builder()
                .id(1L)
                .nombreCompleto("Cliente Test")
                .telefono("70000000")
                .build();

        ProductoVariante variante = crearVariante();
        LoteInventario lote = crearLote(variante);

        VentaRegistroRequest request = new VentaRegistroRequest();
        request.setClienteId(1L);
        request.setFechaVenta(LocalDate.of(2026, 5, 15));
        request.setNumeroComprobante("V-001");
        request.setTipoPago(TipoPagoVenta.CONTADO);

        VentaDetalleRegistroRequest detalle = new VentaDetalleRegistroRequest();
        detalle.setVarianteId(10L);
        detalle.setLoteId(20L);
        detalle.setCantidad(3);
        detalle.setPrecioListaUnitario(80D);
        detalle.setPrecioVentaUnitario(80D);

        request.setDetalles(List.of(detalle));

        AtomicReference<Venta> ventaPersistida = new AtomicReference<>();

        when(clienteService.findById(1L)).thenReturn(cliente);
        when(varianteService.findById(10L)).thenReturn(variante);
        when(loteRepository.findById(20L)).thenReturn(Optional.of(lote));
        when(loteRepository.save(any(LoteInventario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(entradaContableRepository.findFirstByModuloRelacionadoAndRelacionadoId(anyString(), anyLong()))
                .thenReturn(Optional.empty());
        when(accountingService.save(any(EntradaContable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            if (venta.getId() == null) {
                venta.setId(100L);
            }
            ventaPersistida.set(venta);
            return venta;
        });

        when(ventaRepository.findById(100L)).thenAnswer(invocation -> Optional.of(ventaPersistida.get()));

        Venta venta = ventaService.registrarVenta(request);

        assertEquals(100L, venta.getId());
        assertEquals(240D, venta.getTotal());
        assertEquals(1, venta.getDetalles().size());
        assertEquals(2, lote.getCantidadDisponible());
        assertEquals(EstadoLoteInventario.ACTIVO, lote.getEstado());

        assertEquals(90D, venta.getDetalles().get(0).getDetallesLote().get(0).getCostoTotal());
        assertEquals(150D, venta.getDetalles().get(0).getDetallesLote().get(0).getGananciaBruta());

        verify(accountingService).save(argThat(entrada -> entrada.getTipoEntrada() == TipoEntrada.ENTRADA
                && "VENTA_PRODUCTOS".equals(entrada.getCategoria())
                && entrada.getMonto().equals(240D)
                && "VENTA".equals(entrada.getModuloRelacionado())
                && entrada.getRelacionadoId().equals(100L)));

        verify(cuentaPorCobrarRepository, never()).save(any());
    }

    private ProductoVariante crearVariante() {
        CategoriaInventario categoria = CategoriaInventario.builder()
                .id(1L)
                .nombre("PANTALLA")
                .build();

        MarcaInventario marca = MarcaInventario.builder()
                .id(2L)
                .nombre("SAMSUNG")
                .activa(true)
                .build();

        ProductoBase productoBase = ProductoBase.builder()
                .id(5L)
                .codigoBase("BASE-A03")
                .nombreBase("Pantalla Samsung A03 Core")
                .categoria(categoria)
                .marca(marca)
                .modelo("A03 CORE")
                .activo(true)
                .build();

        return ProductoVariante.builder()
                .id(10L)
                .productoBase(productoBase)
                .codigoVariante("VAR-A03-ORIGINAL")
                .calidad("ORIGINAL")
                .tipoPresentacion("DISPLAY")
                .color("NEGRO")
                .precioVentaSugerido(80D)
                .stockMinimo(1)
                .activo(true)
                .build();
    }

    private LoteInventario crearLote(ProductoVariante variante) {
        return LoteInventario.builder()
                .id(20L)
                .variante(variante)
                .codigoLote("LOT-A03-001")
                .codigoProveedor("PROV-001")
                .fechaIngreso(LocalDate.of(2026, 5, 1))
                .cantidadInicial(5)
                .cantidadDisponible(5)
                .costoUnitario(30D)
                .precioVentaUnitario(80D)
                .subtotalCompra(150D)
                .estado(EstadoLoteInventario.ACTIVO)
                .activo(true)
                .visibleEnVentas(true)
                .build();
    }
}
