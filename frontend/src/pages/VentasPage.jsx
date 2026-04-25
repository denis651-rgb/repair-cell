import { useEffect, useMemo, useState } from 'react';
import { Eye, Plus, Printer, RotateCcw, Search, UserPlus } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { formatDate, money, toDateInputValue } from '../utils/formatters';
import '../styles/pages/ventas.css';

const TAMANO = 8;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const detalleInicial = {
  categoriaId: '',
  marcaId: '',
  productoBaseId: '',
  varianteId: '',
  cantidad: 1,
  precioVentaUnitario: '',
};
const devolucionInicial = {
  fechaDevolucion: toDateInputValue(),
  motivoDevolucion: '',
};
const clienteRapidoInicial = {
  nombreCompleto: '',
};

const generarPreviewComprobante = () => {
  const ahora = new Date();
  const pad = (valor) => String(valor).padStart(2, '0');
  return `${ahora.getFullYear()}${pad(ahora.getMonth() + 1)}${pad(ahora.getDate())}${pad(ahora.getHours())}${pad(ahora.getMinutes())}`;
};

const crearVentaInicial = () => ({
  clienteId: '',
  fechaVenta: toDateInputValue(),
  numeroComprobante: generarPreviewComprobante(),
  observaciones: '',
  tipoPago: 'CONTADO',
});

const crearErrorVisualVenta = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion de venta.',
});

const logFrontendRequestError = ({ endpoint, params, error, contexto }) => {
  console.error(`[Ventas] ${contexto}`, {
    endpoint,
    params,
    mensajeBackend: error?.message || 'Sin mensaje',
    error,
  });
};

function getPaymentTone(tipoPago) {
  return tipoPago === 'CREDITO' ? 'is-credit' : 'is-cash';
}

export default function VentasPage() {
  const [ventasPage, setVentasPage] = useState(paginaVacia);
  const [clientes, setClientes] = useState([]);
  const [categorias, setCategorias] = useState([]);
  const [marcas, setMarcas] = useState([]);
  const [productosBase, setProductosBase] = useState([]);
  const [variantes, setVariantes] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);
  const [modalVentaOpen, setModalVentaOpen] = useState(false);
  const [modalDevolucionOpen, setModalDevolucionOpen] = useState(false);
  const [modalClienteRapidoOpen, setModalClienteRapidoOpen] = useState(false);
  const [modalDetalleVentaOpen, setModalDetalleVentaOpen] = useState(false);
  const [ventaForm, setVentaForm] = useState(crearVentaInicial);
  const [detalleForm, setDetalleForm] = useState(detalleInicial);
  const [detallesVenta, setDetallesVenta] = useState([]);
  const [clienteRapidoForm, setClienteRapidoForm] = useState(clienteRapidoInicial);
  const [busquedaVarianteVenta, setBusquedaVarianteVenta] = useState('');
  const [ventaSeleccionada, setVentaSeleccionada] = useState(null);
  const [detalleVentaSeleccionada, setDetalleVentaSeleccionada] = useState(null);
  const [devolucionForm, setDevolucionForm] = useState(devolucionInicial);
  const [detallesDevolucion, setDetallesDevolucion] = useState([]);
  const [error, setError] = useState(null);
  const [ventasError, setVentasError] = useState(null);
  const [guardandoClienteRapido, setGuardandoClienteRapido] = useState(false);
  const [cargandoDetalleVenta, setCargandoDetalleVenta] = useState(false);

  const busquedaDebounced = useDebouncedValue(busqueda, 250);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const cargarCatalogos = async () => {
    try {
      setError(null);
      const [listaClientes, listaCategorias, listaMarcas, listaBase, listaVariantes] = await Promise.all([
        api.get('/clientes'),
        api.get('/inventario/categorias'),
        api.get('/inventario/marcas'),
        api.get('/catalogo/productos-base?soloActivos=true'),
        api.get('/catalogo/productos-variantes?soloActivas=true'),
      ]);
      setClientes(listaClientes || []);
      setCategorias(listaCategorias || []);
      setMarcas(listaMarcas || []);
      setProductosBase(listaBase || []);
      setVariantes((listaVariantes || []).filter((item) => Number(item.stockDisponibleTotal || 0) > 0));
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudieron cargar los catalogos de ventas.', err));
    }
  };

  const cargarVentas = async (paginaObjetivo = pagina) => {
    const params = {
      pagina: paginaObjetivo,
      tamano: TAMANO,
      busqueda: busquedaDebounced,
    };

    try {
      setVentasError(null);
      const respuesta = await api.get('/ventas/paginado', params);
      setVentasPage(respuesta || paginaVacia);
    } catch (err) {
      logFrontendRequestError({
        endpoint: '/ventas/paginado',
        params,
        error: err,
        contexto: 'Fallo al cargar el listado paginado',
      });
      setVentasError({
        titulo: 'No se pudo cargar el listado de ventas.',
        detalle: 'Existe un problema de integridad de datos en el backend o en la respuesta del endpoint.',
        backendDetalle: err?.message || 'Sin detalle devuelto por el servidor.',
      });
    }
  };

  useEffect(() => {
    cargarCatalogos();
  }, []);

  useEffect(() => {
    setPagina(0);
  }, [busquedaDebounced]);

  useEffect(() => {
    cargarVentas(pagina);
  }, [pagina, busquedaDebounced]);

  const productoBaseSeleccionado = useMemo(
    () => productosBase.find((item) => String(item.id) === String(detalleForm.productoBaseId)),
    [productosBase, detalleForm.productoBaseId],
  );

  const varianteSeleccionada = useMemo(
    () => variantes.find((item) => String(item.id) === String(detalleForm.varianteId)),
    [variantes, detalleForm.varianteId],
  );

  const productosBaseFiltradosVenta = useMemo(() => {
    const termino = busquedaVarianteVenta.trim().toLowerCase();

    return productosBase.filter((productoBase) => {
      const coincideCategoria =
        !detalleForm.categoriaId || String(productoBase.categoria?.id) === String(detalleForm.categoriaId);
      const coincideMarca =
        !detalleForm.marcaId || String(productoBase.marca?.id) === String(detalleForm.marcaId);
      const coincideTexto =
        !termino ||
        [
          productoBase.nombreBase,
          productoBase.modelo,
          productoBase.categoria?.nombre,
          productoBase.marca?.nombre,
          ...variantes
            .filter((variante) => String(variante.productoBase?.id) === String(productoBase.id))
            .flatMap((variante) => [variante.calidad, variante.tipoPresentacion]),
        ].some((valor) => String(valor || '').toLowerCase().includes(termino));

      return coincideCategoria && coincideMarca && coincideTexto;
    });
  }, [productosBase, detalleForm.categoriaId, detalleForm.marcaId, busquedaVarianteVenta, variantes]);

  const variantesDisponibles = useMemo(
    () =>
      variantes.filter((variante) => {
        const coincideBase =
          !detalleForm.productoBaseId || String(variante.productoBase?.id) === String(detalleForm.productoBaseId);
        return coincideBase && Number(variante.stockDisponibleTotal || 0) > 0;
      }),
    [variantes, detalleForm.productoBaseId],
  );

  const cantidadReservadaVariante = (varianteId) =>
    detallesVenta
      .filter((detalle) => String(detalle.varianteId) === String(varianteId))
      .reduce((total, detalle) => total + Number(detalle.cantidad || 0), 0);

  useEffect(() => {
    if (!productoBaseSeleccionado) return;
    setDetalleForm((actual) => ({
      ...actual,
      categoriaId: productoBaseSeleccionado.categoria?.id ? String(productoBaseSeleccionado.categoria.id) : '',
      marcaId: productoBaseSeleccionado.marca?.id ? String(productoBaseSeleccionado.marca.id) : '',
    }));
  }, [productoBaseSeleccionado]);

  useEffect(() => {
    if (!varianteSeleccionada) return;
    setDetalleForm((actual) => ({
      ...actual,
      productoBaseId: varianteSeleccionada.productoBase?.id
        ? String(varianteSeleccionada.productoBase.id)
        : actual.productoBaseId,
      precioVentaUnitario: varianteSeleccionada.precioVentaSugerido || '',
    }));
  }, [varianteSeleccionada]);

  const totalVenta = useMemo(
    () => detallesVenta.reduce((sum, detalle) => sum + (detalle.cantidad * detalle.precioVentaUnitario), 0),
    [detallesVenta],
  );

  const abrirVenta = () => {
    setVentaForm(crearVentaInicial());
    setDetalleForm(detalleInicial);
    setDetallesVenta([]);
    setBusquedaVarianteVenta('');
    setClienteRapidoForm(clienteRapidoInicial);
    setModalVentaOpen(true);
  };

  const abrirClienteRapido = () => {
    setClienteRapidoForm(clienteRapidoInicial);
    setModalClienteRapidoOpen(true);
  };

  const guardarClienteRapido = async (event) => {
    event.preventDefault();
    setGuardandoClienteRapido(true);

    try {
      const clienteCreado = await api.post('/clientes', {
        nombreCompleto: clienteRapidoForm.nombreCompleto,
        telefono: 'S/N',
        email: '',
        direccion: '',
        notas: 'Creado rapido desde el modal de ventas.',
      });

      setClientes((actual) => [clienteCreado, ...actual]);
      setVentaForm((actual) => ({ ...actual, clienteId: String(clienteCreado.id) }));
      setClienteRapidoForm(clienteRapidoInicial);
      setModalClienteRapidoOpen(false);
      setError(null);
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudo crear el cliente rapido.', err));
    } finally {
      setGuardandoClienteRapido(false);
    }
  };

  const agregarDetalle = () => {
    if (!detalleForm.varianteId || !varianteSeleccionada || !productoBaseSeleccionado) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'Selecciona un producto base y una variante con stock disponible.',
      });
      return;
    }

    const cantidadNueva = Number(detalleForm.cantidad || 0);
    const cantidadAcumulada = cantidadReservadaVariante(detalleForm.varianteId);
    const stockDisponible = Number(varianteSeleccionada.stockDisponibleTotal || 0);

    if (cantidadNueva <= 0) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'La cantidad debe ser mayor a cero.',
      });
      return;
    }

    if (cantidadNueva + cantidadAcumulada > stockDisponible) {
      setError({
        titulo: 'Stock insuficiente.',
        detalle: `${varianteSeleccionada.calidad || 'La variante'} tiene ${stockDisponible} unidades disponibles y ya reservaste ${cantidadAcumulada} en esta venta.`,
      });
      return;
    }

    const precioLista = Number(varianteSeleccionada.precioVentaSugerido || 0);
    const precioReal = Number(detalleForm.precioVentaUnitario || precioLista);

    if (precioReal < 0) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'El precio real de venta no puede ser negativo.',
      });
      return;
    }

    const detalleNuevo = {
      varianteId: Number(varianteSeleccionada.id),
      productoBaseId: Number(productoBaseSeleccionado.id),
      productoNombre: productoBaseSeleccionado.nombreBase,
      modelo: productoBaseSeleccionado.modelo || '',
      marca: productoBaseSeleccionado.marca?.nombre || '',
      calidad: varianteSeleccionada.calidad || '',
      tipoPresentacion: varianteSeleccionada.tipoPresentacion || '',
      stockDisponible,
      cantidad: cantidadNueva,
      precioListaUnitario: precioLista,
      precioVentaUnitario: precioReal,
    };

    setDetallesVenta((actual) => {
      const indiceExistente = actual.findIndex(
        (detalle) => String(detalle.varianteId) === String(detalleNuevo.varianteId),
      );

      if (indiceExistente === -1) {
        return [...actual, detalleNuevo];
      }

      return actual.map((detalle, indice) => {
        if (indice !== indiceExistente) return detalle;

        return {
          ...detalle,
          cantidad: Number(detalle.cantidad || 0) + detalleNuevo.cantidad,
          precioListaUnitario: detalleNuevo.precioListaUnitario,
          precioVentaUnitario: detalleNuevo.precioVentaUnitario,
        };
      });
    });

    setDetalleForm((actual) => ({
      ...detalleInicial,
      categoriaId: actual.categoriaId,
      marcaId: actual.marcaId,
    }));
    setBusquedaVarianteVenta('');
    setError(null);
  };

  const quitarDetalle = (index) => {
    setDetallesVenta((actual) => actual.filter((_, idx) => idx !== index));
  };

  const guardarVenta = async (event) => {
    event.preventDefault();
    try {
      if (!detallesVenta.length) {
        throw new Error('Agrega al menos una variante a la venta');
      }

      await api.post('/ventas', {
        ...ventaForm,
        clienteId: Number(ventaForm.clienteId),
        detalles: detallesVenta.map((detalle) => ({
          varianteId: detalle.varianteId,
          cantidad: detalle.cantidad,
          precioListaUnitario: detalle.precioListaUnitario,
          precioVentaUnitario: detalle.precioVentaUnitario,
        })),
      });

      setModalVentaOpen(false);
      setVentaForm(crearVentaInicial());
      setDetalleForm(detalleInicial);
      setDetallesVenta([]);
      setBusquedaVarianteVenta('');
      await Promise.all([cargarCatalogos(), cargarVentas(0)]);
      setPagina(0);
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudo guardar la venta.', err));
    }
  };

  const abrirDevolucion = (venta) => {
    api.get(`/ventas/${venta.id}`)
      .then((ventaDetalle) => {
        setVentaSeleccionada(ventaDetalle);
        setDevolucionForm(devolucionInicial);
        setDetallesDevolucion(
          (ventaDetalle.detalles || []).map((detalle) => ({
            ventaDetalleId: detalle.id,
            nombreProducto: `${detalle.sku || ''} ${detalle.nombreProducto || ''}`.trim(),
            cantidadVendida: Number(detalle.cantidad || 0),
            cantidadDevuelta: Number(detalle.cantidadDevuelta || 0),
            cantidadDisponible: Number(detalle.cantidad || 0) - Number(detalle.cantidadDevuelta || 0),
            cantidad: 0,
            precioVentaUnitario: Number(detalle.precioVentaUnitario || 0),
          })),
        );
        setModalDevolucionOpen(true);
      })
      .catch((err) => {
        setError(crearErrorVisualVenta('No se pudo cargar el detalle de la venta.', err));
      });
  };

  const guardarDevolucion = async (event) => {
    event.preventDefault();
    if (!ventaSeleccionada) return;

    try {
      const detallesSeleccionados = detallesDevolucion
        .filter((detalle) => Number(detalle.cantidad || 0) > 0)
        .map((detalle) => ({
          ventaDetalleId: detalle.ventaDetalleId,
          cantidad: Number(detalle.cantidad),
        }));

      if (!detallesSeleccionados.length) {
        throw new Error('Selecciona al menos una cantidad para devolver.');
      }

      await api.post(`/ventas/${ventaSeleccionada.id}/devolucion`, {
        ...devolucionForm,
        detalles: detallesSeleccionados,
      });
      setModalDevolucionOpen(false);
      setVentaSeleccionada(null);
      setDetallesDevolucion([]);
      await Promise.all([cargarCatalogos(), cargarVentas(pagina)]);
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudo registrar la devolucion.', err));
    }
  };

  const abrirDetalleVenta = async (venta) => {
    setCargandoDetalleVenta(true);
    try {
      const detalleVenta = await api.get(`/ventas/${venta.id}`);
      setDetalleVentaSeleccionada(detalleVenta);
      setModalDetalleVentaOpen(true);
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudo cargar el detalle de la venta.', err));
    } finally {
      setCargandoDetalleVenta(false);
    }
  };

  const ventas = ventasPage.content || [];

  return (
    <div className="page-stack sales-page">
      <PageHeader
        title="Ventas"
        subtitle="Venta por variante con consumo FIFO de lotes, precio sugerido y precio real editable."
      >
        <button className="inventory-primary-button compact" onClick={abrirVenta}>
          <Plus size={16} />
          Venta
        </button>
      </PageHeader>

      {error && (
        <div className="sale-alert-floating" role="alert" aria-live="assertive">
          <div className="alert sale-alert-detailed">
            <div className="sale-alert-copy">
              <strong>{error.titulo}</strong>
              <p>{error.detalle}</p>
            </div>
            <button type="button" className="secondary compact" onClick={() => setError(null)}>
              Cerrar
            </button>
          </div>
        </div>
      )}

      <section className="inventory-hero-card sales-toolbar-card">
        <div className="inventory-toolbar">
          <label className="inventory-search">
            <Search size={16} />
            <input
              value={busqueda}
              onChange={(event) => setBusqueda(event.target.value)}
              placeholder="Buscar por cliente, telefono o comprobante"
            />
          </label>
        </div>
      </section>

      <section className="card sales-table-card">
        <div className="inventory-panel-header">
          <div>
            <h3>Historial de ventas</h3>
            <p>Las ventas al contado registran entrada contable; las de credito crean cuenta por cobrar.</p>
          </div>
          <span className="chip">{ventasPage.totalElements} registros</span>
        </div>

        {ventasError && (
          <div className="sale-inline-error" role="alert">
            <div>
              <strong>{ventasError.titulo}</strong>
              <p>{ventasError.detalle}</p>
              <small>Backend: {ventasError.backendDetalle}</small>
            </div>
            <button type="button" className="secondary compact" onClick={() => cargarVentas(pagina)}>
              Reintentar
            </button>
          </div>
        )}

        {ventas.length === 0 ? (
          <EmptyState
            title="Sin ventas registradas"
            description="Registra la primera venta para activar el flujo comercial."
          />
        ) : (
          <>
            <div className="sales-list">
              {ventas.map((venta) => (
                <article key={venta.id} className="sale-card">
                  <div className="sale-card-header">
                    <div>
                      <strong>{venta.clienteNombre || 'Cliente'}</strong>
                      <p>{venta.numeroComprobante || `Venta #${venta.id}`}</p>
                    </div>
                    <div className="sale-card-badges">
                      <span className={`sale-payment-pill ${getPaymentTone(venta.tipoPago)}`}>{venta.tipoPago}</span>
                      <span className={venta.estado === 'DEVUELTA' ? 'badge badge-danger' : 'chip'}>
                        {venta.estado}
                      </span>
                    </div>
                  </div>
                  <div className="sale-card-grid">
                    <span>Fecha: {venta.fechaVenta}</span>
                    <span>Operacion: {venta.tipoPago || 'Sin tipo'}</span>
                    <span>Total: Bs {currency.format(Number(venta.total || 0))}</span>
                  </div>
                  {venta.estado !== 'DEVUELTA' && (
                    <div className="sale-card-actions">
                      <button
                        className="secondary compact sale-eye-button"
                        onClick={() => abrirDetalleVenta(venta)}
                        title="Detalle de venta"
                        aria-label="Detalle de venta"
                        disabled={cargandoDetalleVenta}
                      >
                        <Eye size={14} />
                      </button>
                      <button className="secondary compact" onClick={() => abrirDevolucion(venta)}>
                        <RotateCcw size={14} />
                        Devolucion
                      </button>
                    </div>
                  )}
                </article>
              ))}
            </div>

            <div className="pagination-row inventory-pagination-row">
              <button
                className="secondary"
                type="button"
                disabled={pagina <= 0}
                onClick={() => setPagina((actual) => Math.max(actual - 1, 0))}
              >
                Anterior
              </button>
              <span>
                Pagina {ventasPage.number + 1} de {Math.max(ventasPage.totalPages || 1, 1)}
              </span>
              <button
                className="secondary"
                type="button"
                disabled={pagina + 1 >= (ventasPage.totalPages || 1)}
                onClick={() => setPagina((actual) => actual + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>

      <Modal
        open={modalVentaOpen}
        onClose={() => setModalVentaOpen(false)}
        title="Registrar venta"
        subtitle="El sistema consume FIFO desde los lotes activos y guarda precio lista, precio real, costo y ganancia."
        size="xl"
      >
        <form className="entity-form purchases-form" onSubmit={guardarVenta}>
          <div className="form-grid two-columns">
            <div className="sale-client-field">
              <label>
                <span>Cliente</span>
                <select
                  value={ventaForm.clienteId}
                  onChange={(event) => setVentaForm((actual) => ({ ...actual, clienteId: event.target.value }))}
                  required
                >
                  <option value="">Selecciona un cliente</option>
                  {clientes.map((cliente) => (
                    <option key={cliente.id} value={cliente.id}>
                      {cliente.nombreCompleto} - {cliente.telefono}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="sale-client-add-button"
                onClick={abrirClienteRapido}
                title="Agregar cliente rapido"
                aria-label="Agregar cliente rapido"
              >
                <UserPlus size={16} />
              </button>
            </div>
            <label>
              <span>Fecha</span>
              <input
                type="date"
                value={ventaForm.fechaVenta}
                onChange={(event) => setVentaForm((actual) => ({ ...actual, fechaVenta: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Numero de comprobante</span>
              <input
                value={ventaForm.numeroComprobante}
                onChange={(event) => setVentaForm((actual) => ({ ...actual, numeroComprobante: event.target.value }))}
              />
            </label>
            <label className="sale-payment-field">
              <span>Tipo de pago</span>
              <select
                value={ventaForm.tipoPago}
                onChange={(event) => setVentaForm((actual) => ({ ...actual, tipoPago: event.target.value }))}
              >
                <option value="CONTADO">Contado</option>
                <option value="CREDITO">Credito</option>
              </select>
            </label>
          </div>

          <label>
            <span>Observaciones</span>
            <textarea
              value={ventaForm.observaciones}
              onChange={(event) => setVentaForm((actual) => ({ ...actual, observaciones: event.target.value }))}
            />
          </label>

          <div className="purchase-builder">
            <div className="purchase-builder-header">
              <div>
                <h3>Detalle de la venta</h3>
                <p>Solo puedes vender variantes con stock disponible en lotes activos.</p>
                <p className="sale-helper-text">
                  El precio sugerido viene de la variante; el precio real es editable por cada linea.
                </p>
              </div>
              <span className="chip">{detallesVenta.length} items</span>
            </div>

            <div className="purchase-builder-grid purchase-builder-grid-sales">
              <label>
                <span>Buscar en catalogo</span>
                <input
                  value={busquedaVarianteVenta}
                  onChange={(event) => setBusquedaVarianteVenta(event.target.value)}
                  placeholder="Filtra por nombre, modelo, calidad o marca"
                />
              </label>
              <label>
                <span>Categoria</span>
                <select
                  value={detalleForm.categoriaId}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, categoriaId: event.target.value, productoBaseId: '', varianteId: '' }))}
                >
                  <option value="">Todas las categorias</option>
                  {categorias.map((categoria) => (
                    <option key={categoria.id} value={categoria.id}>
                      {categoria.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Marca</span>
                <select
                  value={detalleForm.marcaId}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, marcaId: event.target.value, productoBaseId: '', varianteId: '' }))}
                >
                  <option value="">Todas las marcas</option>
                  {marcas.map((marca) => (
                    <option key={marca.id} value={marca.id}>
                      {marca.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Producto base</span>
                <select
                  value={detalleForm.productoBaseId}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, productoBaseId: event.target.value, varianteId: '' }))}
                >
                  <option value="">Selecciona un producto base</option>
                  {productosBaseFiltradosVenta.map((productoBase) => (
                    <option key={productoBase.id} value={productoBase.id}>
                      {productoBase.nombreBase} - {productoBase.modelo || 'Sin modelo'}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Variante</span>
                <select
                  value={detalleForm.varianteId}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, varianteId: event.target.value }))}
                >
                  <option value="">Selecciona una variante</option>
                  {variantesDisponibles.map((variante) => (
                    <option key={variante.id} value={variante.id}>
                      {variante.calidad || 'Sin calidad'} - {variante.tipoPresentacion || 'Sin presentacion'}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Precio sugerido</span>
                <input value={varianteSeleccionada?.precioVentaSugerido || ''} readOnly />
              </label>
              <label>
                <span>Stock disponible</span>
                <input value={varianteSeleccionada?.stockDisponibleTotal || ''} readOnly />
              </label>
              <label>
                <span>Cantidad</span>
                <input
                  type="number"
                  min="1"
                  value={detalleForm.cantidad}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, cantidad: event.target.value }))}
                />
              </label>
              <label>
                <span>Precio real de venta</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={detalleForm.precioVentaUnitario}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, precioVentaUnitario: event.target.value }))}
                />
              </label>
            </div>

            <div className="purchase-builder-actions">
              <button type="button" className="secondary" onClick={agregarDetalle}>
                Agregar item
              </button>
            </div>

            {detallesVenta.length === 0 ? (
              <EmptyState
                title="Sin items en la venta"
                description="Agrega variantes al detalle para completar la venta."
              />
            ) : (
              <div className="responsive-table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Base / Variante</th>
                      <th>Cantidad</th>
                      <th>P. lista</th>
                      <th>P. real</th>
                      <th>Subtotal</th>
                      <th>Accion</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detallesVenta.map((detalle, index) => (
                      <tr key={`${detalle.varianteId}-${index}`}>
                        <td>{detalle.productoNombre} - {detalle.modelo || 'Sin modelo'} - {detalle.calidad || 'Sin calidad'} - {detalle.tipoPresentacion || 'Sin presentacion'}</td>
                        <td>{detalle.cantidad}</td>
                        <td>Bs {currency.format(detalle.precioListaUnitario)}</td>
                        <td>Bs {currency.format(detalle.precioVentaUnitario)}</td>
                        <td>Bs {currency.format(detalle.cantidad * detalle.precioVentaUnitario)}</td>
                        <td>
                          <button type="button" className="secondary compact" onClick={() => quitarDetalle(index)}>
                            Quitar
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="purchase-total-strip">
            <div>
              <strong>Total de venta</strong>
              <p>El backend distribuye automaticamente el stock entre lotes activos en orden FIFO.</p>
            </div>
            <span>Bs {currency.format(totalVenta)}</span>
          </div>

          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalVentaOpen(false)}>
              Cancelar
            </button>
            <button type="submit">Guardar venta</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={modalClienteRapidoOpen}
        onClose={() => setModalClienteRapidoOpen(false)}
        title="Nuevo cliente rapido"
        subtitle="Registra solo el nombre para continuar la venta sin salir del modal."
        size="md"
      >
        <form className="entity-form" onSubmit={guardarClienteRapido}>
          <label>
            <span>Nombre del cliente</span>
            <input
              value={clienteRapidoForm.nombreCompleto}
              onChange={(event) => setClienteRapidoForm({ nombreCompleto: event.target.value })}
              placeholder="Ejemplo: Juan Perez"
              required
              autoFocus
            />
          </label>

          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalClienteRapidoOpen(false)}>
              Cancelar
            </button>
            <button type="submit" disabled={guardandoClienteRapido}>
              {guardandoClienteRapido ? 'Guardando...' : 'Crear cliente'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal
        open={modalDetalleVentaOpen}
        onClose={() => setModalDetalleVentaOpen(false)}
        title="Comprobante de venta"
        subtitle="Detalle completo de la venta registrada."
        size="xl"
      >
        {detalleVentaSeleccionada ? (
          <div className="sale-receipt-shell">
            <div className="sale-receipt-actions">
              <button type="button" className="secondary" onClick={() => window.print()}>
                <Printer size={16} />
                Imprimir / PDF
              </button>
            </div>

            <section className="sale-receipt-card">
              <div className="sale-receipt-header">
                <div>
                  <span className="sale-receipt-kicker">Comprobante de venta</span>
                  <h2>{detalleVentaSeleccionada.numeroComprobante || `Venta #${detalleVentaSeleccionada.id}`}</h2>
                  <p>{detalleVentaSeleccionada.cliente?.nombreCompleto || 'Cliente no disponible'}</p>
                </div>
                <div className={`sale-payment-pill ${getPaymentTone(detalleVentaSeleccionada.tipoPago)}`}>
                  {detalleVentaSeleccionada.tipoPago}
                </div>
              </div>

              <div className="sale-receipt-grid">
                <article className="sale-receipt-info">
                  <span>Cliente</span>
                  <strong>{detalleVentaSeleccionada.cliente?.nombreCompleto || 'Sin cliente'}</strong>
                  <p>{detalleVentaSeleccionada.cliente?.telefono || 'Sin telefono registrado'}</p>
                </article>
                <article className="sale-receipt-info">
                  <span>Fecha</span>
                  <strong>{formatDate(detalleVentaSeleccionada.fechaVenta)}</strong>
                  <p>Estado: {detalleVentaSeleccionada.estado || 'REGISTRADA'}</p>
                </article>
                <article className="sale-receipt-info">
                  <span>Total</span>
                  <strong>{money.format(Number(detalleVentaSeleccionada.total || 0))}</strong>
                  <p>{detalleVentaSeleccionada.detalles?.length || 0} items</p>
                </article>
              </div>

              <div className="sale-receipt-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>Cantidad</th>
                      <th>P. lista</th>
                      <th>P. venta</th>
                      <th>Subtotal</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(detalleVentaSeleccionada.detalles || []).map((detalle) => (
                      <tr key={detalle.id}>
                        <td>
                          <strong>{detalle.nombreProducto}</strong>
                          <div>{detalle.calidad || 'Sin calidad'} - {detalle.tipoPresentacion || 'Sin presentacion'}</div>
                        </td>
                        <td>{detalle.cantidad}</td>
                        <td>{money.format(Number(detalle.precioListaUnitario || 0))}</td>
                        <td>{money.format(Number(detalle.precioVentaUnitario || 0))}</td>
                        <td>{money.format(Number(detalle.subtotal || 0))}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="sale-receipt-total">
                <div>
                  <strong>Observaciones</strong>
                  <p>{detalleVentaSeleccionada.observaciones || 'Sin observaciones registradas.'}</p>
                </div>
                <span>{money.format(Number(detalleVentaSeleccionada.total || 0))}</span>
              </div>
            </section>
          </div>
        ) : (
          <div className="empty-state">No hay detalle de venta para mostrar.</div>
        )}
      </Modal>

      <Modal
        open={modalDevolucionOpen}
        onClose={() => setModalDevolucionOpen(false)}
        title="Registrar devolucion"
        subtitle="La devolucion restituye stock a los lotes originalmente consumidos."
      >
        <form className="entity-form" onSubmit={guardarDevolucion}>
          <label>
            <span>Fecha de devolucion</span>
            <input
              type="date"
              value={devolucionForm.fechaDevolucion}
              onChange={(event) => setDevolucionForm((actual) => ({ ...actual, fechaDevolucion: event.target.value }))}
            />
          </label>
          <label>
            <span>Motivo</span>
            <textarea
              value={devolucionForm.motivoDevolucion}
              onChange={(event) => setDevolucionForm((actual) => ({ ...actual, motivoDevolucion: event.target.value }))}
              required
            />
          </label>
          <div className="devolution-detail-list">
            {detallesDevolucion.map((detalle) => (
              <div key={detalle.ventaDetalleId} className="devolution-detail-card">
                <div className="devolution-detail-copy">
                  <strong>{detalle.nombreProducto}</strong>
                  <span>Vendidos: {detalle.cantidadVendida}</span>
                  <span>Ya devueltos: {detalle.cantidadDevuelta}</span>
                  <span>Disponibles: {detalle.cantidadDisponible}</span>
                </div>
                <label className="devolution-quantity-field">
                  <span>Cantidad a devolver</span>
                  <input
                    type="number"
                    min="0"
                    max={detalle.cantidadDisponible}
                    value={detalle.cantidad}
                    onChange={(event) => {
                      const cantidad = Number(event.target.value || 0);
                      setDetallesDevolucion((actual) =>
                        actual.map((item) =>
                          item.ventaDetalleId !== detalle.ventaDetalleId
                            ? item
                            : {
                              ...item,
                              cantidad: Math.min(Math.max(cantidad, 0), item.cantidadDisponible),
                            },
                        ),
                      );
                    }}
                    disabled={detalle.cantidadDisponible <= 0}
                  />
                </label>
              </div>
            ))}
          </div>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalDevolucionOpen(false)}>
              Cancelar
            </button>
            <button type="submit">Confirmar devolucion</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
