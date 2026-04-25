import { useEffect, useMemo, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { toDateInputValue } from '../utils/formatters';
import '../styles/pages/compras.css';

const TAMANO = 8;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const detalleInicial = {
  categoriaId: '',
  marcaId: '',
  productoBaseId: '',
  varianteId: '',
  cantidad: 1,
  precioCompraUnitario: '',
  precioVentaUnitario: '',
};

const generarPreviewComprobante = () => {
  const ahora = new Date();
  const pad = (valor) => String(valor).padStart(2, '0');
  return `${ahora.getFullYear()}${pad(ahora.getMonth() + 1)}${pad(ahora.getDate())}${pad(ahora.getHours())}${pad(ahora.getMinutes())}`;
};

const crearCompraInicial = () => ({
  proveedorId: '',
  fechaCompra: toDateInputValue(),
  numeroComprobante: generarPreviewComprobante(),
  observaciones: '',
  tipoPago: 'CONTADO',
});

const crearErrorVisualCompra = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion de compra.',
});

export default function ComprasPage() {
  const [comprasPage, setComprasPage] = useState(paginaVacia);
  const [proveedores, setProveedores] = useState([]);
  const [categorias, setCategorias] = useState([]);
  const [marcas, setMarcas] = useState([]);
  const [productosBase, setProductosBase] = useState([]);
  const [variantes, setVariantes] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [compraForm, setCompraForm] = useState(crearCompraInicial);
  const [detalleForm, setDetalleForm] = useState(detalleInicial);
  const [busquedaDetalle, setBusquedaDetalle] = useState('');
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
      const [listaProveedores, listaCategorias, listaMarcas, listaBase, listaVariantes] = await Promise.all([
        api.get('/proveedores'),
        api.get('/inventario/categorias'),
        api.get('/inventario/marcas'),
        api.get('/catalogo/productos-base'),
        api.get('/catalogo/productos-variantes'),
      ]);
      setProveedores(listaProveedores || []);
      setCategorias(listaCategorias || []);
      setMarcas(listaMarcas || []);
      setProductosBase(listaBase || []);
      setVariantes(listaVariantes || []);
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
    setCompraForm(crearCompraInicial());
    setDetalleForm(detalleInicial);
    setBusquedaDetalle('');
    setDetallesCompra([]);
    setModalOpen(true);
  };

  const productosBaseFiltrados = useMemo(() => {
    const termino = busquedaDetalle.trim().toLowerCase();

    return productosBase.filter((productoBase) => {
      const coincideCategoria =
        !detalleForm.categoriaId || String(productoBase.categoria?.id) === String(detalleForm.categoriaId);
      const coincideMarca =
        !detalleForm.marcaId || String(productoBase.marca?.id) === String(detalleForm.marcaId);
      const coincideTexto =
        !termino ||
        [
          productoBase.codigoBase,
          productoBase.nombreBase,
          productoBase.modelo,
          productoBase.categoria?.nombre,
          productoBase.marca?.nombre,
        ].some((valor) => String(valor || '').toLowerCase().includes(termino));

      return coincideCategoria && coincideMarca && coincideTexto;
    });
  }, [productosBase, detalleForm.categoriaId, detalleForm.marcaId, busquedaDetalle]);

  const variantesDisponibles = useMemo(
    () =>
      variantes.filter((variante) => {
        const coincideBase =
          !detalleForm.productoBaseId || String(variante.productoBase?.id) === String(detalleForm.productoBaseId);
        return coincideBase && variante.activo !== false;
      }),
    [variantes, detalleForm.productoBaseId],
  );

  const productoBaseSeleccionado = useMemo(
    () => productosBase.find((item) => String(item.id) === String(detalleForm.productoBaseId)),
    [productosBase, detalleForm.productoBaseId],
  );

  const varianteSeleccionada = useMemo(
    () => variantes.find((item) => String(item.id) === String(detalleForm.varianteId)),
    [variantes, detalleForm.varianteId],
  );

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
      precioVentaUnitario: varianteSeleccionada.precioVentaSugerido != null
        ? String(varianteSeleccionada.precioVentaSugerido)
        : '',
    }));
  }, [varianteSeleccionada]);

  const agregarDetalle = () => {
    if (!detalleForm.productoBaseId || !detalleForm.varianteId) {
      setError({
        titulo: 'No se pudo agregar la linea.',
        detalle: 'Selecciona un producto base y una variante antes de agregarla.',
      });
      return;
    }

    if (!productoBaseSeleccionado || !varianteSeleccionada) {
      setError({
        titulo: 'No se pudo agregar la linea.',
        detalle: 'La combinacion de producto base y variante no es valida.',
      });
      return;
    }

    const cantidad = Number(detalleForm.cantidad || 0);
    const costo = Number(detalleForm.precioCompraUnitario || 0);

    if (cantidad <= 0) {
      setError({
        titulo: 'No se pudo agregar la linea.',
        detalle: 'La cantidad debe ser mayor a cero.',
      });
      return;
    }

    if (costo < 0) {
      setError({
        titulo: 'No se pudo agregar la linea.',
        detalle: 'El costo unitario no puede ser negativo.',
      });
      return;
    }

    const detalle = {
      productoBaseId: Number(productoBaseSeleccionado.id),
      varianteId: Number(varianteSeleccionada.id),
      categoriaId: Number(productoBaseSeleccionado.categoria?.id),
      marcaId: Number(productoBaseSeleccionado.marca?.id),
      productoBaseCodigo: productoBaseSeleccionado.codigoBase,
      nombreProducto: productoBaseSeleccionado.nombreBase,
      marca: productoBaseSeleccionado.marca?.nombre || '',
      modelo: productoBaseSeleccionado.modelo || '',
      calidad: varianteSeleccionada.calidad || '',
      tipoPresentacion: varianteSeleccionada.tipoPresentacion || '',
      codigoVariante: varianteSeleccionada.codigoVariante,
      cantidad,
      precioCompraUnitario: costo,
      precioVentaUnitario: Number(detalleForm.precioVentaUnitario || 0),
    };

    setDetallesCompra((actual) => {
      const indiceExistente = actual.findIndex(
        (item) => String(item.varianteId) === String(detalle.varianteId),
      );

      if (indiceExistente === -1) {
        return [...actual, detalle];
      }

      return actual.map((item, indice) => {
        if (indice !== indiceExistente) return item;
        return {
          ...item,
          cantidad: Number(item.cantidad || 0) + detalle.cantidad,
          precioCompraUnitario: detalle.precioCompraUnitario,
          precioVentaUnitario: detalle.precioVentaUnitario,
        };
      });
    });

    setDetalleForm((actual) => ({
      ...detalleInicial,
      categoriaId: actual.categoriaId,
      marcaId: actual.marcaId,
    }));
    setBusquedaDetalle('');
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
        throw new Error('Agrega al menos una linea a la compra');
      }

      await api.post('/compras', {
        ...compraForm,
        proveedorId: Number(compraForm.proveedorId),
        detalles: detallesCompra.map((detalle) => ({
          productoBaseId: detalle.productoBaseId,
          varianteId: detalle.varianteId,
          cantidad: detalle.cantidad,
          precioCompraUnitario: detalle.precioCompraUnitario,
          precioVentaUnitario: detalle.precioVentaUnitario,
        })),
      });

      setModalOpen(false);
      setCompraForm(crearCompraInicial());
      setDetalleForm(detalleInicial);
      setBusquedaDetalle('');
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
      <PageHeader title="Compras" subtitle="Cada linea de compra genera un detalle y un lote nuevo para la variante seleccionada.">
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
            <input
              value={busqueda}
              onChange={(event) => setBusqueda(event.target.value)}
              placeholder="Buscar por proveedor o numero de comprobante"
            />
          </label>
        </div>
      </section>

      <section className="card purchases-table-card">
        <div className="inventory-panel-header">
          <div>
            <h3>Historial de compras</h3>
            <p>Cada compra crea lotes nuevos por linea y conserva trazabilidad de variante y lote origen.</p>
          </div>
          <span className="chip">{comprasPage.totalElements} registros</span>
        </div>

        {compras.length === 0 ? (
          <EmptyState title="Sin compras registradas" description="Registra la primera compra para poblar los lotes por variante." />
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
                        {detalle.sku || 'Sin variante'} · {detalle.nombreProducto} x{detalle.cantidad}
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

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Registrar compra" subtitle="Cada linea genera un lote nuevo. No se reutilizan lotes agotados." size="xl">
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
              <input
                value={compraForm.numeroComprobante}
                onChange={(event) => setCompraForm((actual) => ({ ...actual, numeroComprobante: event.target.value }))}
              />
            </label>
            <label className="purchase-payment-field">
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
                <p>Cada fila usa producto base, variante, cantidad y costo unitario.</p>
                <p className="purchase-helper-text">
                  Si agregas la misma variante dos veces, el sistema consolida la fila y crea un solo lote por linea final.
                </p>
              </div>
              <span className="chip">{detallesCompra.length} items</span>
            </div>

            <div className="purchase-builder-grid">
              <label>
                <span>Buscar en catalogo</span>
                <input
                  value={busquedaDetalle}
                  onChange={(event) => setBusquedaDetalle(event.target.value)}
                  placeholder="Filtra por codigo base, nombre, modelo o marca"
                />
              </label>
              <label>
                <span>Categoria</span>
                <select value={detalleForm.categoriaId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, categoriaId: event.target.value, productoBaseId: '', varianteId: '' }))}>
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
                <select value={detalleForm.marcaId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, marcaId: event.target.value, productoBaseId: '', varianteId: '' }))}>
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
                <select value={detalleForm.productoBaseId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, productoBaseId: event.target.value, varianteId: '' }))}>
                  <option value="">Selecciona un producto base</option>
                  {productosBaseFiltrados.map((productoBase) => (
                    <option key={productoBase.id} value={productoBase.id}>
                      {productoBase.codigoBase} - {productoBase.nombreBase} - {productoBase.modelo || 'Sin modelo'}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Variante</span>
                <select value={detalleForm.varianteId} onChange={(event) => setDetalleForm((actual) => ({ ...actual, varianteId: event.target.value }))}>
                  <option value="">Selecciona una variante</option>
                  {variantesDisponibles.map((variante) => (
                    <option key={variante.id} value={variante.id}>
                      {variante.codigoVariante} - {variante.calidad} - {variante.tipoPresentacion || 'Sin presentacion'}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Precio de venta sugerido</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={detalleForm.precioVentaUnitario}
                  onChange={(event) => setDetalleForm((actual) => ({ ...actual, precioVentaUnitario: event.target.value }))}
                  placeholder="Se carga desde la variante, pero puedes editarlo"
                />
              </label>
              <label>
                <span>Cantidad</span>
                <input type="number" min="1" value={detalleForm.cantidad} onChange={(event) => setDetalleForm((actual) => ({ ...actual, cantidad: event.target.value }))} />
              </label>
              <label>
                <span>Costo unitario</span>
                <input type="number" min="0" step="0.01" value={detalleForm.precioCompraUnitario} onChange={(event) => setDetalleForm((actual) => ({ ...actual, precioCompraUnitario: event.target.value }))} />
              </label>
            </div>

            <div className="purchase-builder-actions">
              <button type="button" className="secondary" onClick={agregarDetalle}>
                Agregar item
              </button>
            </div>

            {detallesCompra.length === 0 ? (
              <EmptyState title="Sin items en la compra" description="Agrega variantes al detalle antes de guardar." />
            ) : (
              <div className="responsive-table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Base / Variante</th>
                      <th>Marca</th>
                      <th>Cantidad</th>
                      <th>Costo</th>
                      <th>P. venta sugerido</th>
                      <th>Subtotal</th>
                      <th>Accion</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detallesCompra.map((detalle, index) => (
                      <tr key={`${detalle.varianteId}-${index}`}>
                        <td>{detalle.productoBaseCodigo} · {detalle.nombreProducto} · {detalle.codigoVariante}</td>
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
              <p>Cada linea generara un lote nuevo relacionado a su variante y a esta compra.</p>
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
