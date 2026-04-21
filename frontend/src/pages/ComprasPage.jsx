import { useEffect, useMemo, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import '../styles/pages/compras.css';

const TAMANO = 8;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const compraInicial = {
  proveedorId: '',
  fechaCompra: new Date().toISOString().slice(0, 10),
  numeroComprobante: '',
  observaciones: '',
  tipoPago: 'CONTADO',
};
const detalleInicial = {
  productoId: '',
  categoriaId: '',
  marcaId: '',
  sku: '',
  nombreProducto: '',
  calidad: '',
  cantidad: 1,
  precioCompraUnitario: '',
  precioVentaUnitario: '',
};

const obtenerNombreMarca = (marca) => (typeof marca === 'string' ? marca : marca?.nombre || '');
const crearErrorVisualCompra = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion de compra.',
});
const construirClaveDetalleCompra = (detalle) => {
  if (detalle.productoId) {
    return `producto:${detalle.productoId}`;
  }

  return [
    `categoria:${detalle.categoriaId || ''}`,
    `marca:${detalle.marcaId || ''}`,
    `sku:${String(detalle.sku || '').trim().toLowerCase()}`,
    `nombre:${String(detalle.nombreProducto || '').trim().toLowerCase()}`,
    `calidad:${String(detalle.calidad || '').trim().toLowerCase()}`,
  ].join('|');
};

export default function ComprasPage() {
  const [comprasPage, setComprasPage] = useState(paginaVacia);
  const [proveedores, setProveedores] = useState([]);
  const [categorias, setCategorias] = useState([]);
  const [marcas, setMarcas] = useState([]);
  const [productos, setProductos] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [compraForm, setCompraForm] = useState(compraInicial);
  const [detalleForm, setDetalleForm] = useState(detalleInicial);
  const [busquedaProductoDetalle, setBusquedaProductoDetalle] = useState('');
  const [detallesCompra, setDetallesCompra] = useState([]);
  const [error, setError] = useState(null);

  const busquedaDebounced = useDebouncedValue(busqueda, 250);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const cargarCatalogos = async () => {
    try {
      setError(null);
      const [listaProveedores, listaCategorias, listaMarcas, listaProductos] = await Promise.all([
        api.get('/proveedores'),
        api.get('/inventario/categorias'),
        api.get('/inventario/marcas'),
        api.get('/inventario/productos'),
      ]);
      setProveedores(listaProveedores || []);
      setCategorias(listaCategorias || []);
      setMarcas(listaMarcas || []);
      setProductos(listaProductos || []);
    } catch (err) {
      setError(crearErrorVisualCompra('No se pudieron cargar los catalogos de compras.', err));
    }
  };

  const cargarCompras = async (paginaObjetivo = pagina) => {
    try {
      setError(null);
      const respuesta = await api.get('/compras/paginado', {
        pagina: paginaObjetivo,
        tamano: TAMANO,
        busqueda: busquedaDebounced,
      });
      setComprasPage(respuesta || paginaVacia);
    } catch (err) {
      setError(crearErrorVisualCompra('No se pudo cargar el historial de compras.', err));
    }
  };

  useEffect(() => {
    cargarCatalogos();
  }, []);

  useEffect(() => {
    setPagina(0);
  }, [busquedaDebounced]);

  useEffect(() => {
    cargarCompras(pagina);
  }, [pagina, busquedaDebounced]);

  const abrirModal = () => {
    setCompraForm(compraInicial);
    setDetalleForm(detalleInicial);
    setBusquedaProductoDetalle('');
    setDetallesCompra([]);
    setModalOpen(true);
  };

  const productoSeleccionado = useMemo(
    () => productos.find((producto) => String(producto.id) === String(detalleForm.productoId)),
    [productos, detalleForm.productoId],
  );

  const productosFiltradosDetalle = useMemo(() => {
    const termino = busquedaProductoDetalle.trim().toLowerCase();

    return productos.filter((producto) => {
      const coincideCategoria =
        !detalleForm.categoriaId || String(producto.categoria?.id) === String(detalleForm.categoriaId);
      const coincideMarca =
        !detalleForm.marcaId || String(producto.marca?.id) === String(detalleForm.marcaId);
      const coincideTexto =
        !termino ||
        [producto.nombre, producto.sku, obtenerNombreMarca(producto.marca), producto.categoria?.nombre]
          .some((valor) => String(valor || '').toLowerCase().includes(termino));

      return coincideCategoria && coincideMarca && coincideTexto;
    });
  }, [productos, detalleForm.categoriaId, detalleForm.marcaId, busquedaProductoDetalle]);

  useEffect(() => {
    if (!productoSeleccionado) return;
    setDetalleForm((actual) => ({
      ...actual,
      categoriaId: productoSeleccionado.categoria?.id ? String(productoSeleccionado.categoria.id) : '',
      marcaId: productoSeleccionado.marca?.id ? String(productoSeleccionado.marca.id) : '',
      sku: productoSeleccionado.sku || '',
      nombreProducto: productoSeleccionado.nombre || '',
      calidad: productoSeleccionado.calidad || '',
      precioCompraUnitario: productoSeleccionado.costoUnitario || '',
      precioVentaUnitario: productoSeleccionado.precioVenta || '',
    }));
  }, [productoSeleccionado]);

  const agregarDetalle = () => {
    if (!detalleForm.categoriaId || !detalleForm.marcaId || !detalleForm.nombreProducto) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'Completa categoria, marca y nombre del producto antes de agregarlo.',
      });
      return;
    }

    const marcaSeleccionada = marcas.find((marca) => String(marca.id) === String(detalleForm.marcaId));
    if (!marcaSeleccionada) {
      setError({
        titulo: 'No se pudo agregar el item.',
        detalle: 'Selecciona una marca valida para el producto.',
      });
      return;
    }

    const detalle = {
      productoId: detalleForm.productoId ? Number(detalleForm.productoId) : null,
      categoriaId: Number(detalleForm.categoriaId),
      marcaId: Number(detalleForm.marcaId),
      sku: detalleForm.sku,
      nombreProducto: detalleForm.nombreProducto,
      marca: marcaSeleccionada.nombre,
      calidad: detalleForm.calidad,
      cantidad: Number(detalleForm.cantidad),
      precioCompraUnitario: Number(detalleForm.precioCompraUnitario),
      precioVentaUnitario: Number(detalleForm.precioVentaUnitario),
    };

    const claveNuevoDetalle = construirClaveDetalleCompra(detalle);

    setDetallesCompra((actual) => {
      const indiceExistente = actual.findIndex(
        (item) => construirClaveDetalleCompra(item) === claveNuevoDetalle,
      );

      if (indiceExistente === -1) {
        return [...actual, detalle];
      }

      return actual.map((item, indice) => {
        if (indice !== indiceExistente) return item;

        // Si el usuario agrega el mismo item de nuevo, consolidamos la fila y
        // actualizamos cantidades/precios con el ultimo valor ingresado.
        return {
          ...item,
          cantidad: Number(item.cantidad || 0) + Number(detalle.cantidad || 0),
          precioCompraUnitario: detalle.precioCompraUnitario,
          precioVentaUnitario: detalle.precioVentaUnitario,
          calidad: detalle.calidad || item.calidad,
          sku: detalle.sku || item.sku,
          nombreProducto: detalle.nombreProducto || item.nombreProducto,
          marca: detalle.marca || item.marca,
        };
      });
    });

    setDetalleForm(detalleInicial);
    setBusquedaProductoDetalle('');
    setError(null);
  };

  const quitarDetalle = (index) => {
    setDetallesCompra((actual) => actual.filter((_, idx) => idx !== index));
  };

  const totalCompra = useMemo(
    () => detallesCompra.reduce((sum, detalle) => sum + (detalle.cantidad * detalle.precioCompraUnitario), 0),
    [detallesCompra],
  );

  const guardarCompra = async (event) => {
    event.preventDefault();
    try {
      if (!detallesCompra.length) {
        throw new Error('Agrega al menos un item a la compra');
      }

      await api.post('/compras', {
        ...compraForm,
        proveedorId: Number(compraForm.proveedorId),
        detalles: detallesCompra.map((detalle) => ({
          productoId: detalle.productoId,
          categoriaId: detalle.categoriaId,
          marcaId: detalle.marcaId,
          sku: detalle.sku,
          nombreProducto: detalle.nombreProducto,
          calidad: detalle.calidad,
          cantidad: detalle.cantidad,
          precioCompraUnitario: detalle.precioCompraUnitario,
          precioVentaUnitario: detalle.precioVentaUnitario,
        })),
      });

      setModalOpen(false);
      setCompraForm(compraInicial);
      setDetalleForm(detalleInicial);
      setBusquedaProductoDetalle('');
      setDetallesCompra([]);
      await Promise.all([cargarCatalogos(), cargarCompras(0)]);
      setPagina(0);
    } catch (err) {
      setError(crearErrorVisualCompra('No se pudo guardar la compra.', err));
    }
  };

  const compras = comprasPage.content || [];

  return (
    <div className="page-stack purchases-page">
      <PageHeader title="Compras" subtitle="Registro de abastecimiento con impacto automatico en inventario y contabilidad.">
        <button className="inventory-primary-button compact" onClick={abrirModal}>
          <Plus size={16} />
          Compra
        </button>
      </PageHeader>

      {error && (
        <div className="alert purchase-alert-detailed">
          <div className="purchase-alert-copy">
            <strong>{error.titulo}</strong>
            <p>{error.detalle}</p>
          </div>
          <button type="button" className="secondary compact" onClick={() => setError(null)}>
            Cerrar
          </button>
        </div>
      )}

      <section className="inventory-hero-card purchases-toolbar-card">
        <div className="inventory-toolbar">
          <label className="inventory-search">
            <Search size={16} />
            <input value={busqueda} onChange={(event) => setBusqueda(event.target.value)} placeholder="Buscar por proveedor o numero de comprobante" />
          </label>
        </div>
      </section>

      <section className="card purchases-table-card">
        <div className="inventory-panel-header">
          <div>
            <h3>Historial de compras</h3>
            <p>Cada compra aumenta stock y, si es al contado, registra salida contable automatica.</p>
          </div>
          <span className="chip">{comprasPage.totalElements} registros</span>
        </div>

        {compras.length === 0 ? (
          <EmptyState title="Sin compras registradas" description="Registra la primera compra para poblar el inventario desde proveedores." />
        ) : (
          <>
            <div className="purchases-list">
              {compras.map((compra) => (
                <article key={compra.id} className="purchase-card">
                  <div className="purchase-card-header">
                    <div>
                      <strong>{compra.proveedor?.nombreComercial || 'Proveedor'}</strong>
                      <p>{compra.numeroComprobante || `Compra #${compra.id}`}</p>
                    </div>
                    <span className="chip">{compra.tipoPago}</span>
                  </div>
                  <div className="purchase-card-grid">
                    <span>Fecha: {compra.fechaCompra}</span>
                    <span>Items: {compra.detalles?.length || 0}</span>
                    <span>Total: Bs {currency.format(Number(compra.total || 0))}</span>
                  </div>
                  <div className="purchase-card-items">
                    {(compra.detalles || []).slice(0, 3).map((detalle) => (
                      <span key={detalle.id || `${detalle.sku}-${detalle.nombreProducto}`} className="purchase-item-chip">
                        {detalle.nombreProducto} x{detalle.cantidad}
                      </span>
                    ))}
                  </div>
                </article>
              ))}
            </div>

            <div className="pagination-row inventory-pagination-row">
              <button className="secondary" type="button" disabled={pagina <= 0} onClick={() => setPagina((actual) => Math.max(actual - 1, 0))}>
                Anterior
              </button>
              <span>
                Pagina {comprasPage.number + 1} de {Math.max(comprasPage.totalPages || 1, 1)}
              </span>
              <button className="secondary" type="button" disabled={pagina + 1 >= (comprasPage.totalPages || 1)} onClick={() => setPagina((actual) => actual + 1)}>
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Registrar compra" subtitle="La compra aumentara stock y generara movimiento de inventario." size="xl">
        <form className="entity-form purchases-form" onSubmit={guardarCompra}>
          <div className="form-grid two-columns">
            <label>
              <span>Proveedor</span>
              <select value={compraForm.proveedorId} onChange={(event) => setCompraForm((actual) => ({ ...actual, proveedorId: event.target.value }))} required>
                <option value="">Selecciona un proveedor</option>
                {proveedores.map((proveedor) => (
                  <option key={proveedor.id} value={proveedor.id}>
                    {proveedor.nombreComercial}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Fecha</span>
              <input type="date" value={compraForm.fechaCompra} onChange={(event) => setCompraForm((actual) => ({ ...actual, fechaCompra: event.target.value }))} required />
            </label>
            <label>
              <span>Numero de comprobante</span>
              <input value={compraForm.numeroComprobante} onChange={(event) => setCompraForm((actual) => ({ ...actual, numeroComprobante: event.target.value }))} />
            </label>
            <label>
              <span>Tipo de pago</span>
              <select value={compraForm.tipoPago} onChange={(event) => setCompraForm((actual) => ({ ...actual, tipoPago: event.target.value }))}>
                <option value="CONTADO">Contado</option>
                <option value="CREDITO">Credito</option>
              </select>
            </label>
          </div>

          <label>
            <span>Observaciones</span>
            <textarea value={compraForm.observaciones} onChange={(event) => setCompraForm((actual) => ({ ...actual, observaciones: event.target.value }))} />
          </label>

          <div className="purchase-builder">
            <div className="purchase-builder-header">
              <div>
                <h3>Detalle de la compra</h3>
                <p>Puedes elegir un producto existente o registrar uno nuevo desde la misma compra.</p>
                <p className="purchase-helper-text">
                  Si agregas el mismo item dos veces, el sistema consolida la fila y suma la cantidad.
                </p>
              </div>
              <span className="chip">{detallesCompra.length} items</span>
            </div>

            <div className="purchase-builder-grid">
              <label>
                <span>Producto existente</span>
                <input
                  value={busquedaProductoDetalle}
                  onChange={(event) => setBusquedaProductoDetalle(event.target.value)}
                  placeholder="Filtra por nombre, SKU, categoria o marca"
                />
                <select value={detalleForm.productoId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, productoId: event.target.value }))}>
                  <option value="">Crear como producto nuevo</option>
                  {productosFiltradosDetalle.map((producto) => (
                    <option key={producto.id} value={producto.id}>
                      {producto.nombre} - {obtenerNombreMarca(producto.marca) || 'Sin marca'} - {producto.categoria?.nombre || 'Sin categoria'}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Categoria</span>
                <select value={detalleForm.categoriaId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, categoriaId: event.target.value }))}>
                  <option value="">Selecciona categoria</option>
                  {categorias.map((categoria) => (
                    <option key={categoria.id} value={categoria.id}>
                      {categoria.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Marca</span>
                <select value={detalleForm.marcaId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, marcaId: event.target.value }))}>
                  <option value="">Selecciona marca</option>
                  {marcas.map((marca) => (
                    <option key={marca.id} value={marca.id}>
                      {marca.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>SKU</span>
                <input value={detalleForm.sku} onChange={(event) => setDetalleForm((actual) => ({ ...actual, sku: event.target.value }))} />
              </label>
              <label>
                <span>Nombre / modelo</span>
                <input value={detalleForm.nombreProducto} onChange={(event) => setDetalleForm((actual) => ({ ...actual, nombreProducto: event.target.value }))} />
              </label>
              <label>
                <span>Calidad</span>
                <input value={detalleForm.calidad} onChange={(event) => setDetalleForm((actual) => ({ ...actual, calidad: event.target.value }))} placeholder="Original, Incell, OLED..." />
              </label>
              <label>
                <span>Cantidad</span>
                <input type="number" min="1" value={detalleForm.cantidad} onChange={(event) => setDetalleForm((actual) => ({ ...actual, cantidad: event.target.value }))} />
              </label>
              <label>
                <span>Precio compra</span>
                <input type="number" min="0" step="0.01" value={detalleForm.precioCompraUnitario} onChange={(event) => setDetalleForm((actual) => ({ ...actual, precioCompraUnitario: event.target.value }))} />
              </label>
              <label>
                <span>Precio venta</span>
                <input type="number" min="0" step="0.01" value={detalleForm.precioVentaUnitario} onChange={(event) => setDetalleForm((actual) => ({ ...actual, precioVentaUnitario: event.target.value }))} />
              </label>
            </div>

            <div className="purchase-builder-actions">
              <button type="button" className="secondary" onClick={agregarDetalle}>
                Agregar item
              </button>
            </div>

            {detallesCompra.length === 0 ? (
              <EmptyState title="Sin items en la compra" description="Agrega productos al detalle antes de guardar." />
            ) : (
              <div className="responsive-table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>Marca</th>
                      <th>Cantidad</th>
                      <th>P. compra</th>
                      <th>P. venta</th>
                      <th>Subtotal</th>
                      <th>Accion</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detallesCompra.map((detalle, index) => (
                      <tr key={`${detalle.nombreProducto}-${index}`}>
                        <td>{detalle.nombreProducto}</td>
                        <td>{detalle.marca}</td>
                        <td>{detalle.cantidad}</td>
                        <td>Bs {currency.format(detalle.precioCompraUnitario)}</td>
                        <td>Bs {currency.format(detalle.precioVentaUnitario)}</td>
                        <td>Bs {currency.format(detalle.cantidad * detalle.precioCompraUnitario)}</td>
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
              <strong>Total de compra</strong>
              <p>Si es al contado, tambien se registra como salida contable.</p>
            </div>
            <span>Bs {currency.format(totalCompra)}</span>
          </div>

          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalOpen(false)}>Cancelar</button>
            <button type="submit">Guardar compra</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
