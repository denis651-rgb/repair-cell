package com.store.repair.service;

import com.store.repair.domain.CategoriaInventario;
import com.store.repair.domain.Compra;
import com.store.repair.domain.EntradaContable;
import com.store.repair.domain.EstadoLoteInventario;
import com.store.repair.domain.LoteInventario;
import com.store.repair.domain.MarcaInventario;
import com.store.repair.domain.ProductoBase;
import com.store.repair.domain.ProductoVariante;
import com.store.repair.domain.Proveedor;
import com.store.repair.domain.TipoEntrada;
import com.store.repair.domain.TipoPagoCompra;
import com.store.repair.dto.CompraDetalleRegistroRequest;
import com.store.repair.dto.CompraRegistroRequest;
import com.store.repair.dto.LoteInventarioRequest;
import com.store.repair.repository.CompraRepository;
import com.store.repair.repository.EntradaContableRepository;
import com.store.repair.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompraServiceTest {

    private CompraRepository compraRepository;
    private ProveedorService proveedorService;
    private ProductoVarianteService productoVarianteService;
    private LoteInventarioService loteInventarioService;
    private AccountingService accountingService;
    private EntradaContableRepository entradaContableRepository;
    private ComprobanteService comprobanteService;
    private ProductoVarianteRepository productoVarianteRepository;
    private CompraService compraService;

    @BeforeEach
    void setUp() {
        compraRepository = mock(CompraRepository.class);
        proveedorService = mock(ProveedorService.class);
        productoVarianteService = mock(ProductoVarianteService.class);
        loteInventarioService = mock(LoteInventarioService.class);
        accountingService = mock(AccountingService.class);
        entradaContableRepository = mock(EntradaContableRepository.class);
        comprobanteService = mock(ComprobanteService.class);
        productoVarianteRepository = mock(ProductoVarianteRepository.class);

        compraService = new CompraService(
                compraRepository,
                proveedorService,
                productoVarianteService,
                loteInventarioService,
                accountingService,
                entradaContableRepository,
                comprobanteService,
                productoVarianteRepository);
    }

    @Test
    void registrarCompra_sinDetalles_debeFallar() {
        CompraRegistroRequest request = new CompraRegistroRequest();
        request.setProveedorId(1L);
        request.setTipoPago(TipoPagoCompra.CONTADO);
        request.setDetalles(List.of());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> compraService.registrarCompra(request));

        assertEquals("La compra debe incluir al menos una variante", ex.getMessage());
    }

    @Test
    void registrarCompra_contado_debeCrearLoteYSalidaContable() {
        Proveedor proveedor = Proveedor.builder()
                .id(1L)
                .nombreComercial("Proveedor Test")
                .activo(true)
                .build();

        ProductoVariante variante = crearVariante();

        CompraRegistroRequest request = new CompraRegistroRequest();
        request.setProveedorId(1L);
        request.setFechaCompra(LocalDate.of(2026, 5, 15));
        request.setNumeroComprobante("C-001");
        request.setTipoPago(TipoPagoCompra.CONTADO);

        CompraDetalleRegistroRequest detalle = new CompraDetalleRegistroRequest();
        detalle.setVarianteId(10L);
        detalle.setCantidad(4);
        detalle.setPrecioCompraUnitario(35D);
        detalle.setPrecioVentaUnitario(90D);
        detalle.setCodigoProveedor("PROV-A03");

        request.setDetalles(List.of(detalle));

        AtomicReference<Compra> compraPersistida = new AtomicReference<>();

        when(proveedorService.findById(1L)).thenReturn(proveedor);
        when(productoVarianteService.findById(10L)).thenReturn(variante);
        when(productoVarianteRepository.save(any(ProductoVariante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(entradaContableRepository.findFirstByModuloRelacionadoAndRelacionadoId(anyString(), anyLong()))
                .thenReturn(Optional.empty());
        when(accountingService.save(any(EntradaContable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(compraRepository.save(any(Compra.class))).thenAnswer(invocation -> {
            Compra compra = invocation.getArgument(0);
            if (compra.getId() == null) {
                compra.setId(200L);
            }
            compraPersistida.set(compra);
            return compra;
        });

        when(compraRepository.findById(200L)).thenAnswer(invocation -> Optional.of(compraPersistida.get()));

        when(loteInventarioService.save(isNull(), any(LoteInventarioRequest.class))).thenAnswer(invocation -> {
            LoteInventarioRequest loteRequest = invocation.getArgument(1);

            return LoteInventario.builder()
                    .id(300L)
                    .variante(variante)
                    .proveedor(proveedor)
                    .codigoLote(loteRequest.getCodigoLote())
                    .codigoProveedor(loteRequest.getCodigoProveedor())
                    .fechaIngreso(loteRequest.getFechaIngreso())
                    .cantidadInicial(loteRequest.getCantidadInicial())
                    .cantidadDisponible(loteRequest.getCantidadDisponible())
                    .costoUnitario(loteRequest.getCostoUnitario())
                    .precioVentaUnitario(loteRequest.getPrecioVentaUnitario())
                    .subtotalCompra(loteRequest.getSubtotalCompra())
                    .compraId(loteRequest.getCompraId())
                    .estado(EstadoLoteInventario.ACTIVO)
                    .activo(true)
                    .visibleEnVentas(true)
                    .build();
        });

        Compra compra = compraService.registrarCompra(request);

        assertEquals(200L, compra.getId());
        assertEquals(140D, compra.getTotal());
        assertEquals(1, compra.getDetalles().size());
        assertEquals("LOT-VAR-A03-ORIGINAL-C001-1", compra.getDetalles().get(0).getCodigoLote());

        ArgumentCaptor<LoteInventarioRequest> loteCaptor = ArgumentCaptor.forClass(LoteInventarioRequest.class);
        verify(loteInventarioService).save(isNull(), loteCaptor.capture());

        LoteInventarioRequest loteCreado = loteCaptor.getValue();
        assertEquals(10L, loteCreado.getVarianteId());
        assertEquals(1L, loteCreado.getProveedorId());
        assertEquals(4, loteCreado.getCantidadInicial());
        assertEquals(4, loteCreado.getCantidadDisponible());
        assertEquals(35D, loteCreado.getCostoUnitario());
        assertEquals(90D, loteCreado.getPrecioVentaUnitario());
        assertEquals(140D, loteCreado.getSubtotalCompra());

        verify(accountingService).save(argThat(entrada -> entrada.getTipoEntrada() == TipoEntrada.SALIDA
                && "COMPRA_INVENTARIO".equals(entrada.getCategoria())
                && entrada.getMonto().equals(140D)
                && "COMPRA".equals(entrada.getModuloRelacionado())
                && entrada.getRelacionadoId().equals(200L)));
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
}