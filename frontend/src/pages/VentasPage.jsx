import { useEffect, useMemo, useState } from 'react';
import { Plus, RotateCcw, Search } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import '../styles/pages/ventas.css';

const TAMANO = 8;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const ventaInicial = {
  clienteId: '',
  fechaVenta: new Date().toISOString().slice(0, 10),
  numeroComprobante: '',
  observaciones: '',
  tipoPago: 'CONTADO',
};
const detalleInicial = {
  productoId: '',
  cantidad: 1,
  precioVentaUnitario: '',
};
const devolucionInicial = {
  fechaDevolucion: new Date().toISOString().slice(0, 10),
  motivoDevolucion: '',
};

const obtenerNombreMarca = (marca) => (typeof marca === 'string' ? marca : marca?.nombre || '');
const crearErrorVisualVenta = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion de venta.',
});

export default function VentasPage() {
  const [ventasPage, setVentasPage] = useState(paginaVacia);
  const [clientes, setClientes] = useState([]);
  const [productos, setProductos] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);
  const [modalVentaOpen, setModalVentaOpen] = useState(false);
  const [modalDevolucionOpen, setModalDevolucionOpen] = useState(false);
  const [ventaForm, setVentaForm] = useState(ventaInicial);
  const [detalleForm, setDetalleForm] = useState(detalleInicial);
  const [detallesVenta, setDetallesVenta] = useState([]);
  const [busquedaProductoVenta, setBusquedaProductoVenta] = useState('');
  const [ventaSeleccionada, setVentaSeleccionada] = useState(null);
  const [devolucionForm, setDevolucionForm] = useState(devolucionInicial);
  const [detallesDevolucion, setDetallesDevolucion] = useState([]);
  const [error, setError] = useState(null);

  const busquedaDebounced = useDebouncedValue(busqueda, 250);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const cargarCatalogos = async () => {
    try {
      setError(null);
      const [listaClientes, listaProductos] = await Promise.all([
        api.get('/clientes'),
        api.get('/inventario/productos'),
      ]);
      setClientes(listaClientes || []);
      setProductos((listaProductos || []).map((producto) => ({
        ...producto,
        marca: obtenerNombreMarca(producto.marca),
      })));
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudieron cargar los catalogos de ventas.', err));
    }
  };

  const cargarVentas = async (paginaObjetivo = pagina) => {
    try {
      setError(null);
      const respuesta = await api.get('/ventas/paginado', {
        pagina: paginaObjetivo,
        tamano: TAMANO,
        busqueda: busquedaDebounced,
      });
      setVentasPage(respuesta || paginaVacia);
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudo cargar el historial de ventas.', err));
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

  const productoSeleccionado = useMemo(
    () => productos.find((producto) => String(producto.id) === String(detalleForm.productoId)),
    [productos, detalleForm.productoId],
  );

  const productosFiltradosVenta = useMemo(() => {
    const termino = busquedaProductoVenta.trim().toLowerCase();
    if (!termino) return productos;

    return productos.filter((producto) =>
      [producto.nombre, producto.sku, producto.marca, producto.categoria?.nombre]
        .some((valor) => String(valor || '').toLowerCase().includes(termino)),
    );
  }, [productos, busquedaProductoVenta]);

  const cantidadReservadaProducto = (productoId) =>
    detallesVenta
      .filter((detalle) => String(detalle.productoId) === String(productoId))
      .reduce((total, detalle) => total + Number(detalle.cantidad || 0), 0);

  useEffect(() => {
    if (!productoSeleccionado) return;
    setDetalleForm((actual) => ({
      ...actual,
      precioVentaUnitario: productoSeleccionado.precioVenta || '',
    }));
  }, [productoSeleccionado]);

  const totalVenta = useMemo(
    () => detallesVenta.reduce((sum, detalle) => sum + (detalle.cantidad * detalle.precioVentaUnitario), 0),
    [detallesVenta],
  );

  const abrirVenta = () => {
    setVentaForm(ventaInicial);
    setDetalleForm(detalleInicial);
    setDetallesVenta([]);
    setBusquedaProductoVenta('');
    setModalVentaOpen(true);
  };

  const agregarDetalle = () => {
    if (!detalleForm.productoId) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'Selecciona un producto para agregarlo a la venta.',
      });
      return;
    }

    const producto = productos.find((item) => String(item.id) === String(detalleForm.productoId));
    if (!producto) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'El producto seleccionado no existe.',
      });
      return;
    }

    const cantidadNueva = Number(detalleForm.cantidad || 0);
    const cantidadAcumulada = cantidadReservadaProducto(detalleForm.productoId);
    const stockDisponible = Number(producto.cantidadStock || 0);

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
        detalle: `${producto.nombre} tiene ${stockDisponible} unidades disponibles y ya reservaste ${cantidadAcumulada} en esta venta.`,
      });
      return;
    }

    const detalleNuevo = {
      productoId: Number(detalleForm.productoId),
      productoNombre: producto.nombre,
      marca: obtenerNombreMarca(producto.marca),
      cantidad: cantidadNueva,
      precioVentaUnitario: Number(detalleForm.precioVentaUnitario || producto.precioVenta || 0),
    };

    setDetallesVenta((actual) => {
      const indiceExistente = actual.findIndex(
        (detalle) => String(detalle.productoId) === String(detalleNuevo.productoId),
      );

      if (indiceExistente === -1) {
        return [...actual, detalleNuevo];
      }

      // Consolidamos el mismo producto para que la venta quede mas clara y el
      // backend reciba un detalle limpio, sin filas repetidas.
      return actual.map((detalle, indice) => {
        if (indice !== indiceExistente) return detalle;

        return {
          ...detalle,
          cantidad: Number(detalle.cantidad || 0) + detalleNuevo.cantidad,
          precioVentaUnitario: detalleNuevo.precioVentaUnitario,
        };
      });
    });

    setDetalleForm(detalleInicial);
    setBusquedaProductoVenta('');
    setError(null);
  };

  const quitarDetalle = (index) => {
    setDetallesVenta((actual) => actual.filter((_, idx) => idx !== index));
  };

  const guardarVenta = async (event) => {
    event.preventDefault();
    try {
      if (!detallesVenta.length) {
        throw new Error('Agrega al menos un producto a la venta');
      }

      await api.post('/ventas', {
        ...ventaForm,
        clienteId: Number(ventaForm.clienteId),
        detalles: detallesVenta.map((detalle) => ({
          productoId: detalle.productoId,
          cantidad: detalle.cantidad,
          precioVentaUnitario: detalle.precioVentaUnitario,
        })),
      });

      setModalVentaOpen(false);
      setVentaForm(ventaInicial);
      setDetalleForm(detalleInicial);
      setDetallesVenta([]);
      setBusquedaProductoVenta('');
      await Promise.all([cargarCatalogos(), cargarVentas(0)]);
      setPagina(0);
    } catch (err) {
      setError(crearErrorVisualVenta('No se pudo guardar la venta.', err));
    }
  };

  const abrirDevolucion = (venta) => {
    setVentaSeleccionada(venta);
    setDevolucionForm(devolucionInicial);
    setDetallesDevolucion(
      (venta.detalles || []).map((detalle) => ({
        ventaDetalleId: detalle.id,
        nombreProducto: detalle.nombreProducto,
        cantidadVendida: Number(detalle.cantidad || 0),
        cantidadDevuelta: Number(detalle.cantidadDevuelta || 0),
        cantidadDisponible: Number(detalle.cantidad || 0) - Number(detalle.cantidadDevuelta || 0),
        cantidad: 0,
        precioVentaUnitario: Number(detalle.precioVentaUnitario || 0),
      })),
    );
    setModalDevolucionOpen(true);
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

  const ventas = ventasPage.content || [];

  return (
    <div className="page-stack sales-page">
      <PageHeader
        title="Ventas"
        subtitle="Venta directa de productos a clientes o tecnicos, al contado o al credito."
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
                      <strong>{venta.cliente?.nombreCompleto || 'Cliente'}</strong>
                      <p>{venta.numeroComprobante || `Venta #${venta.id}`}</p>
                    </div>
                    <div className="sale-card-badges">
                      <span className="chip">{venta.tipoPago}</span>
                      <span className={venta.estado === 'DEVUELTA' ? 'badge badge-danger' : 'chip'}>
                        {venta.estado}
                      </span>
                    </div>
                  </div>
                  <div className="sale-card-grid">
                    <span>Fecha: {venta.fechaVenta}</span>
                    <span>Items: {venta.detalles?.length || 0}</span>
                    <span>Total: Bs {currency.format(Number(venta.total || 0))}</span>
                  </div>
                  <div className="sale-card-items">
                    {(venta.detalles || []).slice(0, 4).map((detalle) => (
                      <span key={detalle.id || `${detalle.sku}-${detalle.nombreProducto}`} className="purchase-item-chip">
                        {detalle.nombreProducto} x{detalle.cantidad}
                      </span>
                    ))}
                  </div>
                  {venta.estado !== 'DEVUELTA' && (
                    <div className="sale-card-actions">
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
        subtitle="Descuenta stock automaticamente y registra el efecto contable segun el tipo de pago."
        size="xl"
      >
        <form className="entity-form purchases-form" onSubmit={guardarVenta}>
          <div className="form-grid two-columns">
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
                    {cliente.nombreCompleto} • {cliente.telefono}
                  </option>
                ))}
              </select>
            </label>
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
            <label>
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
                <p>Solo puedes vender productos con stock disponible.</p>
                <p className="sale-helper-text">
                  Si agregas el mismo producto dos veces, el sistema consolida la fila y suma la cantidad.
                </p>
              </div>
              <span className="chip">{detallesVenta.length} items</span>
            </div>

            <div className="purchase-builder-grid purchase-builder-grid-sales">
              <label>
                <span>Producto</span>
                <input
                  value={busquedaProductoVenta}
                  onChange={(event) => setBusquedaProductoVenta(event.target.value)}
                  placeholder="Filtra por nombre, SKU, marca o categoria"
                />
                <select
                  value={detalleForm.productoId}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, productoId: event.target.value }))}
                >
                  <option value="">Selecciona un producto</option>
                  {productosFiltradosVenta.map((producto) => (
                    <option key={producto.id} value={producto.id}>
                      {producto.nombre} • {producto.marca || 'Sin marca'} • Stock {producto.cantidadStock}
                    </option>
                  ))}
                </select>
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
                <span>Precio de venta</span>
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
                description="Agrega productos al detalle para completar la venta."
              />
            ) : (
              <div className="responsive-table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>Marca</th>
                      <th>Cantidad</th>
                      <th>Precio</th>
                      <th>Subtotal</th>
                      <th>Accion</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detallesVenta.map((detalle, index) => (
                      <tr key={`${detalle.productoId}-${index}`}>
                        <td>{detalle.productoNombre}</td>
                        <td>{detalle.marca || 'Sin marca'}</td>
                        <td>{detalle.cantidad}</td>
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
              <p>Contado: entrada contable. Credito: cuenta por cobrar.</p>
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
        open={modalDevolucionOpen}
        onClose={() => setModalDevolucionOpen(false)}
        title="Registrar devolucion"
        subtitle="La devolucion revierte stock y el efecto financiero asociado."
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
