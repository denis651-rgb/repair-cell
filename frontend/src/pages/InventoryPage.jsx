import { useEffect, useMemo, useState } from 'react';
import { ArrowDownToLine, ArrowUpToLine, FolderTree, PackagePlus, PencilLine, Tags, Trash2 } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { isValidSku, normalizeSku, suggestSku } from '../utils/sku';
import '../styles/pages/inventario.css';

const TAMANO_PRODUCTOS = 10;
const TAMANO_MOVIMIENTOS = 8;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const categoriaInicial = { nombre: '', descripcion: '' };
const marcaInicial = { nombre: '', descripcion: '', activa: true };
const productoInicial = {
  categoriaId: '',
  marcaId: '',
  sku: '',
  nombre: '',
  descripcion: '',
  calidad: '',
  costoUnitario: 0,
  precioVenta: 0,
  stockInicial: 0,
  stockMinimo: 0,
  activo: true,
};
const ajusteInicial = { cantidad: 1, tipoMovimiento: 'ENTRADA', descripcion: '', costoUnitario: '' };

const obtenerNombreMarca = (marca) => (typeof marca === 'string' ? marca : marca?.nombre || '');
const obtenerIdMarca = (marca) => (typeof marca === 'object' && marca?.id ? String(marca.id) : '');
const crearErrorVisual = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion.',
});
const etiquetasMovimiento = {
  ENTRADA: 'Entrada',
  SALIDA: 'Salida',
  AJUSTE: 'Ajuste',
};

function formatearFechaHora(valor) {
  if (!valor) return 'Sin fecha';
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return valor;
  return new Intl.DateTimeFormat('es-BO', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(fecha);
}

export default function InventoryPage() {
  const [categorias, setCategorias] = useState([]);
  const [marcas, setMarcas] = useState([]);
  const [productosPage, setProductosPage] = useState(paginaVacia);
  const [movimientosPage, setMovimientosPage] = useState(paginaVacia);
  const [productosStockBajo, setProductosStockBajo] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [categoriaFiltro, setCategoriaFiltro] = useState('');
  const [marcaFiltro, setMarcaFiltro] = useState('');
  const [tipoMovimientoFiltro, setTipoMovimientoFiltro] = useState('');
  const [paginaProductos, setPaginaProductos] = useState(0);
  const [paginaMovimientos, setPaginaMovimientos] = useState(0);

  const [modalCategoriaOpen, setModalCategoriaOpen] = useState(false);
  const [modalMarcaOpen, setModalMarcaOpen] = useState(false);
  const [modalProductoOpen, setModalProductoOpen] = useState(false);
  const [modalAjusteOpen, setModalAjusteOpen] = useState(false);

  const [categoriaForm, setCategoriaForm] = useState(categoriaInicial);
  const [marcaForm, setMarcaForm] = useState(marcaInicial);
  const [productoForm, setProductoForm] = useState(productoInicial);
  const [ajusteForm, setAjusteForm] = useState(ajusteInicial);

  const [categoriaEditando, setCategoriaEditando] = useState(null);
  const [marcaEditando, setMarcaEditando] = useState(null);
  const [productoEditando, setProductoEditando] = useState(null);
  const [productoSeleccionado, setProductoSeleccionado] = useState(null);
  const [error, setError] = useState(null);
  const [skuSugerido, setSkuSugerido] = useState('');
  const [skuTocado, setSkuTocado] = useState(false);
  const [productosSimilares, setProductosSimilares] = useState([]);
  const [cargandoSku, setCargandoSku] = useState(false);

  const busquedaDebounced = useDebouncedValue(busqueda, 250);
  const skuDebounced = useDebouncedValue(productoForm.sku, 200);
  const nombreDebounced = useDebouncedValue(productoForm.nombre, 200);
  const calidadDebounced = useDebouncedValue(productoForm.calidad, 200);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const cargarResumen = async () => {
    try {
      setError('');
      const [listaCategorias, listaMarcas, stockBajo] = await Promise.all([
        api.get('/inventario/categorias'),
        api.get('/inventario/marcas'),
        api.get('/inventario/productos/stock-bajo'),
      ]);
      setCategorias(listaCategorias || []);
      setMarcas(listaMarcas || []);
      setProductosStockBajo(stockBajo || []);
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el resumen de inventario.', err));
    }
  };

  const cargarProductos = async (pagina = paginaProductos) => {
    try {
      setError('');
      const respuesta = await api.get('/inventario/productos/paginado', {
        pagina,
        tamano: TAMANO_PRODUCTOS,
        busqueda: busquedaDebounced,
        categoriaId: categoriaFiltro || undefined,
        marcaId: marcaFiltro || undefined,
      });
      setProductosPage(respuesta || paginaVacia);
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el listado de productos.', err));
    }
  };

  const cargarMovimientos = async (pagina = paginaMovimientos) => {
    try {
      setError('');
      const respuesta = await api.get('/inventario/movimientos/paginado', {
        pagina,
        tamano: TAMANO_MOVIMIENTOS,
        busqueda: busquedaDebounced,
        categoriaId: categoriaFiltro || undefined,
        marcaId: marcaFiltro || undefined,
        tipoMovimiento: tipoMovimientoFiltro || undefined,
      });
      setMovimientosPage(respuesta || paginaVacia);
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el kardex de inventario.', err));
    }
  };

  useEffect(() => {
    cargarResumen();
  }, []);

  useEffect(() => {
    setPaginaProductos(0);
    setPaginaMovimientos(0);
  }, [busquedaDebounced, categoriaFiltro, marcaFiltro, tipoMovimientoFiltro]);

  useEffect(() => {
    cargarProductos(paginaProductos);
  }, [paginaProductos, busquedaDebounced, categoriaFiltro, marcaFiltro]);

  useEffect(() => {
    cargarMovimientos(paginaMovimientos);
  }, [paginaMovimientos, busquedaDebounced, categoriaFiltro, marcaFiltro, tipoMovimientoFiltro]);

  const productos = productosPage.content || [];

  const abrirCategorias = () => {
    setCategoriaEditando(null);
    setCategoriaForm(categoriaInicial);
    setModalCategoriaOpen(true);
  };

  const abrirMarcas = () => {
    setMarcaEditando(null);
    setMarcaForm(marcaInicial);
    setModalMarcaOpen(true);
  };

  const abrirProductoNuevo = () => {
    setProductoEditando(null);
    setProductoForm(productoInicial);
    setSkuTocado(false);
    setSkuSugerido('');
    setProductosSimilares([]);
    setModalProductoOpen(true);
  };

  const editarCategoria = (categoria) => {
    setCategoriaEditando(categoria);
    setCategoriaForm({
      nombre: categoria.nombre || '',
      descripcion: categoria.descripcion || '',
    });
  };

  const editarMarca = (marca) => {
    setMarcaEditando(marca);
    setMarcaForm({
      nombre: marca.nombre || '',
      descripcion: marca.descripcion || '',
      activa: marca.activa ?? true,
    });
  };

  const editarProducto = (producto) => {
    setProductoEditando(producto);
    setProductoForm({
      categoriaId: producto.categoria?.id ? String(producto.categoria.id) : '',
      marcaId: obtenerIdMarca(producto.marca),
      sku: producto.sku || '',
      nombre: producto.nombre || '',
      descripcion: producto.descripcion || '',
      calidad: producto.calidad || '',
      costoUnitario: producto.costoUnitario ?? 0,
      precioVenta: producto.precioVenta ?? 0,
      stockInicial: 0,
      stockMinimo: producto.stockMinimo ?? 0,
      activo: producto.activo ?? true,
    });
    setSkuTocado(true);
    setSkuSugerido(producto.sku || '');
    setProductosSimilares([]);
    setModalProductoOpen(true);
  };

  useEffect(() => {
    if (!modalProductoOpen) return;

    const categoriaNombre = categorias.find((categoria) => String(categoria.id) === String(productoForm.categoriaId))?.nombre;
    const marcaNombre = marcas.find((marca) => String(marca.id) === String(productoForm.marcaId))?.nombre;
    const sugerenciaLocal = suggestSku({
      categoria: categoriaNombre,
      marca: marcaNombre,
      nombreModelo: nombreDebounced,
      calidad: calidadDebounced,
    });

    if (!skuTocado && sugerenciaLocal) {
      setProductoForm((actual) => ({ ...actual, sku: sugerenciaLocal }));
    }

    setSkuSugerido(sugerenciaLocal);

    const puedeConsultar = productoForm.categoriaId && productoForm.marcaId && nombreDebounced.trim();
    if (!puedeConsultar) {
      setProductosSimilares([]);
      return;
    }

    let cancelado = false;
    setCargandoSku(true);

    api.get('/inventario/productos/sku/sugerencia', {
      categoriaId: Number(productoForm.categoriaId),
      marcaId: Number(productoForm.marcaId),
      nombreModelo: nombreDebounced,
      calidad: calidadDebounced,
      skuActual: skuDebounced,
      productoId: productoEditando?.id,
    })
      .then((respuesta) => {
        if (cancelado) return;
        setSkuSugerido(respuesta?.skuSugerido || sugerenciaLocal);
        setProductosSimilares(respuesta?.productosSimilares || []);
        if (!skuTocado && respuesta?.skuSugerido) {
          setProductoForm((actual) => ({ ...actual, sku: respuesta.skuSugerido }));
        }
      })
      .catch(() => {
        if (!cancelado) {
          setProductosSimilares([]);
        }
      })
      .finally(() => {
        if (!cancelado) {
          setCargandoSku(false);
        }
      });

    return () => {
      cancelado = true;
    };
  }, [
    modalProductoOpen,
    productoForm.categoriaId,
    productoForm.marcaId,
    nombreDebounced,
    calidadDebounced,
    skuDebounced,
    skuTocado,
    categorias,
    marcas,
    productoEditando?.id,
  ]);

  const guardarCategoria = async (event) => {
    event.preventDefault();
    try {
      if (categoriaEditando) {
        await api.put(`/inventario/categorias/${categoriaEditando.id}`, categoriaForm);
      } else {
        await api.post('/inventario/categorias', categoriaForm);
      }
      setCategoriaForm(categoriaInicial);
      setCategoriaEditando(null);
      await Promise.all([cargarResumen(), cargarProductos(0)]);
      setPaginaProductos(0);
    } catch (err) {
      setError(
        crearErrorVisual(
          categoriaEditando ? 'No se pudo actualizar la categoria.' : 'No se pudo guardar la categoria.',
          err,
        ),
      );
    }
  };

  const guardarMarca = async (event) => {
    event.preventDefault();
    try {
      if (marcaEditando) {
        await api.put(`/inventario/marcas/${marcaEditando.id}`, marcaForm);
      } else {
        await api.post('/inventario/marcas', marcaForm);
      }
      setMarcaForm(marcaInicial);
      setMarcaEditando(null);
      await Promise.all([cargarResumen(), cargarProductos(0)]);
      setPaginaProductos(0);
    } catch (err) {
      setError(
        crearErrorVisual(
          marcaEditando ? 'No se pudo actualizar la marca.' : 'No se pudo guardar la marca.',
          err,
        ),
      );
    }
  };

  const eliminarCategoria = async (categoria) => {
    if (!window.confirm(`Eliminar la categoria "${categoria.nombre}"?`)) return;
    try {
      await api.delete(`/inventario/categorias/${categoria.id}`);
      if (String(categoriaFiltro) === String(categoria.id)) {
        setCategoriaFiltro('');
      }
      await Promise.all([cargarResumen(), cargarProductos(0)]);
      setPaginaProductos(0);
    } catch (err) {
      setError(crearErrorVisual(`No se pudo eliminar la categoria "${categoria.nombre}".`, err));
    }
  };

  const eliminarMarca = async (marca) => {
    if (!window.confirm(`Eliminar la marca "${marca.nombre}"?`)) return;
    try {
      await api.delete(`/inventario/marcas/${marca.id}`);
      if (String(marcaFiltro) === String(marca.id)) {
        setMarcaFiltro('');
      }
      await Promise.all([cargarResumen(), cargarProductos(0)]);
      setPaginaProductos(0);
    } catch (err) {
      setError(crearErrorVisual(`No se pudo eliminar la marca "${marca.nombre}".`, err));
    }
  };

  const guardarProducto = async (event) => {
    event.preventDefault();
    try {
      const skuNormalizado = normalizeSku(productoForm.sku);
      if (!skuNormalizado) {
        throw new Error('El SKU es obligatorio.');
      }
      if (!isValidSku(skuNormalizado)) {
        throw new Error('El SKU debe usar solo letras, numeros y guiones. Ejemplo: BAT-SAM-A04-ORI');
      }

      const payload = {
        sku: skuNormalizado,
        nombre: productoForm.nombre,
        descripcion: productoForm.descripcion,
        calidad: productoForm.calidad,
        costoUnitario: Number(productoForm.costoUnitario || 0),
        precioVenta: Number(productoForm.precioVenta || 0),
        stockMinimo: Number(productoForm.stockMinimo || 0),
        activo: productoForm.activo ?? true,
      };

      if (!productoEditando) {
        payload.cantidadStock = Number(productoForm.stockInicial || 0);
      }

      const query = {
        categoriaId: Number(productoForm.categoriaId),
        marcaId: Number(productoForm.marcaId),
      };

      if (productoEditando) {
        await api.put(`/inventario/productos/${productoEditando.id}`, payload, query);
      } else {
        await api.post('/inventario/productos', payload, query);
      }

      setProductoForm(productoInicial);
      setProductoEditando(null);
      setSkuSugerido('');
      setSkuTocado(false);
      setProductosSimilares([]);
      setModalProductoOpen(false);
      await Promise.all([cargarResumen(), cargarProductos(0), cargarMovimientos(0)]);
      setPaginaProductos(0);
      setPaginaMovimientos(0);
    } catch (err) {
      setError(
        crearErrorVisual(
          productoEditando ? 'No se pudo actualizar el producto.' : 'No se pudo guardar el producto.',
          err,
        ),
      );
    }
  };

  const eliminarProducto = async (producto) => {
    if (!window.confirm(`Eliminar el producto "${producto.nombre}"?`)) return;
    try {
      await api.delete(`/inventario/productos/${producto.id}`);
      await Promise.all([cargarResumen(), cargarProductos(0)]);
      setPaginaProductos(0);
    } catch (err) {
      setError(crearErrorVisual(`No se pudo eliminar el producto "${producto.nombre}".`, err));
    }
  };

  const abrirAjuste = (producto, tipoMovimiento) => {
    setProductoSeleccionado(producto);
    setAjusteForm({
      ...ajusteInicial,
      tipoMovimiento,
      costoUnitario: tipoMovimiento === 'ENTRADA' ? String(producto.costoUnitario || '') : '',
    });
    setModalAjusteOpen(true);
  };

  const guardarAjuste = async (event) => {
    event.preventDefault();
    if (!productoSeleccionado) return;

    try {
      await api.post(`/inventario/productos/${productoSeleccionado.id}/stock`, {
        cantidad: Number(ajusteForm.cantidad),
        tipoMovimiento: ajusteForm.tipoMovimiento,
        descripcion: ajusteForm.descripcion,
        costoUnitario: ajusteForm.costoUnitario === '' ? null : Number(ajusteForm.costoUnitario),
      });
      setModalAjusteOpen(false);
      setProductoSeleccionado(null);
      setAjusteForm(ajusteInicial);
      await Promise.all([cargarResumen(), cargarProductos(paginaProductos), cargarMovimientos(0)]);
      setPaginaMovimientos(0);
    } catch (err) {
      setError(crearErrorVisual('No se pudo guardar el ajuste de inventario.', err));
    }
  };

  const cerrarModalProducto = () => {
    setModalProductoOpen(false);
    setProductoEditando(null);
    setProductoForm(productoInicial);
    setSkuSugerido('');
    setSkuTocado(false);
    setProductosSimilares([]);
  };

  const skuNoEditable = Boolean(productoEditando && productoEditando.skuEditable === false);
  const skuNormalizadoPreview = normalizeSku(productoForm.sku);
  const skuInvalido = Boolean(productoForm.sku) && !isValidSku(productoForm.sku);

  return (
    <div className="page-stack inventory-page">
      <PageHeader
        title="Inventario"
        subtitle="Control comercial de repuestos con CRUD de categorias y marcas, filtros y precios visibles."
      >
        <div className="inventory-header-actions">
          <button
            className="secondary inventory-ghost-button inventory-icon-button"
            onClick={abrirCategorias}
            title="Administrar categorias"
            aria-label="Administrar categorias"
          >
            <FolderTree size={18} />
          </button>
          <button
            className="secondary inventory-ghost-button inventory-icon-button"
            onClick={abrirMarcas}
            title="Administrar marcas"
            aria-label="Administrar marcas"
          >
            <Tags size={18} />
          </button>
          <button
            className="inventory-primary-button inventory-icon-button"
            onClick={abrirProductoNuevo}
            title="Registrar producto"
            aria-label="Registrar producto"
          >
            <PackagePlus size={18} />
          </button>
        </div>
      </PageHeader>

      {error && (
        <div className="alert inventory-alert-detailed">
          <div className="inventory-alert-copy">
            <strong>{error.titulo}</strong>
            <p>{error.detalle}</p>
          </div>
          <button type="button" className="secondary compact" onClick={() => setError(null)}>
            Cerrar
          </button>
        </div>
      )}

      <section className="inventory-hero-card inventory-toolbar-card">
        <div className="inventory-toolbar">
          <label className="inventory-search">
            <input
              value={busqueda}
              onChange={(event) => setBusqueda(event.target.value)}
              placeholder="Buscar por producto, SKU, categoria o marca"
            />
          </label>

          <div className="inventory-select-filters">
            <select value={categoriaFiltro} onChange={(event) => setCategoriaFiltro(event.target.value)}>
              <option value="">Todas las categorias</option>
              {categorias.map((categoria) => (
                <option key={categoria.id} value={categoria.id}>
                  {categoria.nombre}
                </option>
              ))}
            </select>

            <select value={marcaFiltro} onChange={(event) => setMarcaFiltro(event.target.value)}>
              <option value="">Todas las marcas</option>
              {marcas.map((marca) => (
                <option key={marca.id} value={marca.id}>
                  {marca.nombre}
                </option>
              ))}
            </select>

            <select value={tipoMovimientoFiltro} onChange={(event) => setTipoMovimientoFiltro(event.target.value)}>
              <option value="">Todo el kardex</option>
              <option value="ENTRADA">Entradas</option>
              <option value="SALIDA">Salidas</option>
              <option value="AJUSTE">Ajustes</option>
            </select>
          </div>
        </div>
      </section>

      {productosStockBajo.length > 0 && (
        <div className="low-stock-banner">
          <div className="low-stock-copy">
            <strong>Productos con stock bajo</strong>
            <p>
              Hay {productosStockBajo.length} producto{productosStockBajo.length === 1 ? '' : 's'} por debajo del minimo.
            </p>
          </div>
        </div>
      )}

      <section className="inventory-layout inventory-layout-full">
        <div className="inventory-main-panel card">
          <div className="inventory-panel-header inventory-panel-header-tight">
            <div>
              <h3>Listado de productos</h3>
              <p>Ficha maestra del inventario. El stock se mueve desde compras, ventas, reparaciones o ajustes.</p>
            </div>
            <span className="chip">{productosPage.totalElements} visibles</span>
          </div>

          {productos.length === 0 ? (
            <EmptyState
              title="No encontramos productos"
              description="Prueba otro filtro o registra el primer producto."
            />
          ) : (
            <>
              <div className="inventory-products-table inventory-products-table-wide">
                <div className="inventory-products-head inventory-products-head-wide">
                  <span>Producto</span>
                  <span>SKU</span>
                  <span>Marca</span>
                  <span>Categoria</span>
                  <span>Calidad</span>
                  <span>Stock</span>
                  <span>Precio venta</span>
                  <span>Acciones</span>
                </div>

                <div className="inventory-products-list">
                  {productos.map((producto) => {
                    const stockBajo = Number(producto.cantidadStock || 0) <= Number(producto.stockMinimo || 0);
                    const descripcionSecundaria = [producto.sku || 'Sin SKU', producto.descripcion || null]
                      .filter(Boolean)
                      .join(' · ');

                    return (
                      <article
                        key={producto.id}
                        className="inventory-product-row inventory-product-row-wide inventory-product-row-polished"
                      >
                        <div className="inventory-product-cell inventory-product-info">
                          <div>
                            <strong>{producto.nombre}</strong>
                            <p>{descripcionSecundaria}</p>
                          </div>
                        </div>

                        <div className="inventory-product-cell">
                          <strong>{producto.sku || 'Sin SKU'}</strong>
                        </div>
                        <div className="inventory-product-cell">{obtenerNombreMarca(producto.marca) || 'Sin marca'}</div>
                        <div className="inventory-product-cell">
                          <span className="inventory-category-pill">{producto.categoria?.nombre || 'Sin categoria'}</span>
                        </div>
                        <div className="inventory-product-cell">{producto.calidad || 'Sin calidad'}</div>
                        <div className="inventory-product-cell">
                          <div className="inventory-stock-summary">
                            <strong className={stockBajo ? 'inventory-stock-danger' : ''}>{producto.cantidadStock}</strong>
                            <span>Min. {producto.stockMinimo}</span>
                          </div>
                        </div>
                        <div className="inventory-product-cell inventory-price-cell">
                          Bs {currency.format(Number(producto.precioVenta || 0))}
                        </div>
                        <div className="inventory-product-cell">
                          <div className="inventory-actions-inline">
                            <button
                              className="secondary button-inline"
                              onClick={() => editarProducto(producto)}
                              title="Editar producto"
                              aria-label="Editar producto"
                            >
                              <PencilLine size={14} />
                            </button>
                            <button
                              className="secondary button-inline"
                              onClick={() => abrirAjuste(producto, 'ENTRADA')}
                              title="Aumentar stock"
                              aria-label="Registrar entrada de stock"
                            >
                              <ArrowUpToLine size={14} />
                            </button>
                            <button
                              className="secondary button-inline inventory-danger-soft"
                              onClick={() => abrirAjuste(producto, 'SALIDA')}
                              title="Reducir stock"
                              aria-label="Registrar salida de stock"
                            >
                              <ArrowDownToLine size={14} />
                            </button>
                            <button
                              className="secondary button-inline inventory-danger-soft"
                              onClick={() => eliminarProducto(producto)}
                              title="Eliminar producto"
                              aria-label="Eliminar producto"
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </div>

              <div className="pagination-row inventory-pagination-row">
                <button
                  className="secondary"
                  type="button"
                  disabled={paginaProductos <= 0}
                  onClick={() => setPaginaProductos((actual) => Math.max(actual - 1, 0))}
                >
                  Anterior
                </button>
                <span>
                  Pagina {productosPage.number + 1} de {Math.max(productosPage.totalPages || 1, 1)}
                </span>
                <button
                  className="secondary"
                  type="button"
                  disabled={paginaProductos + 1 >= (productosPage.totalPages || 1)}
                  onClick={() => setPaginaProductos((actual) => actual + 1)}
                >
                  Siguiente
                </button>
              </div>
            </>
          )}
        </div>
      </section>

      <section className="inventory-layout inventory-layout-full">
        <div className="inventory-main-panel card">
          <div className="inventory-panel-header inventory-panel-header-tight">
            <div>
              <h3>Kardex</h3>
              <p>Fuente operativa del stock: cada entrada, salida o ajuste queda trazado con su referencia.</p>
            </div>
            <span className="chip">{movimientosPage.totalElements} movimientos</span>
          </div>

          {(movimientosPage.content || []).length === 0 ? (
            <EmptyState
              title="Sin movimientos de inventario"
              description="Cuando registres compras, ventas, reparaciones o ajustes, el kardex aparecera aqui."
            />
          ) : (
            <>
              <div className="responsive-table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Producto</th>
                      <th>Tipo</th>
                      <th>Cantidad</th>
                      <th>Stock</th>
                      <th>Referencia</th>
                      <th>Detalle</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(movimientosPage.content || []).map((movimiento) => (
                      <tr key={movimiento.id}>
                        <td>{formatearFechaHora(movimiento.fechaMovimiento)}</td>
                        <td>
                          <strong>{movimiento.producto?.nombre || 'Producto'}</strong>
                          <div>{movimiento.producto?.sku || 'Sin SKU'}</div>
                        </td>
                        <td>{etiquetasMovimiento[movimiento.tipoMovimiento] || movimiento.tipoMovimiento}</td>
                        <td>{movimiento.cantidad}</td>
                        <td>
                          {movimiento.stockAnterior ?? 0} {'->'} {movimiento.stockPosterior ?? 0}
                        </td>
                        <td>
                          {movimiento.tipoReferencia || 'Sin referencia'}
                          {movimiento.referenciaId ? ` #${movimiento.referenciaId}` : ''}
                        </td>
                        <td>{movimiento.descripcion || 'Sin descripcion'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="pagination-row inventory-pagination-row">
                <button
                  className="secondary"
                  type="button"
                  disabled={paginaMovimientos <= 0}
                  onClick={() => setPaginaMovimientos((actual) => Math.max(actual - 1, 0))}
                >
                  Anterior
                </button>
                <span>
                  Pagina {movimientosPage.number + 1} de {Math.max(movimientosPage.totalPages || 1, 1)}
                </span>
                <button
                  className="secondary"
                  type="button"
                  disabled={paginaMovimientos + 1 >= (movimientosPage.totalPages || 1)}
                  onClick={() => setPaginaMovimientos((actual) => actual + 1)}
                >
                  Siguiente
                </button>
              </div>
            </>
          )}
        </div>
      </section>

      <Modal
        open={modalCategoriaOpen}
        onClose={() => setModalCategoriaOpen(false)}
        title="CRUD de categorias"
        subtitle="Crea, edita o elimina categorias maestras del inventario."
        size="lg"
      >
        <div className="inventory-admin-modal">
          <form className="entity-form" onSubmit={guardarCategoria}>
            <div className="form-grid two-columns">
              <label>
                <span>Nombre</span>
                <input
                  value={categoriaForm.nombre}
                  onChange={(event) => setCategoriaForm((actual) => ({ ...actual, nombre: event.target.value }))}
                  required
                />
              </label>
              <label>
                <span>Descripcion</span>
                <input
                  value={categoriaForm.descripcion}
                  onChange={(event) => setCategoriaForm((actual) => ({ ...actual, descripcion: event.target.value }))}
                />
              </label>
            </div>
            <div className="modal-actions-row">
              {categoriaEditando ? (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setCategoriaEditando(null);
                    setCategoriaForm(categoriaInicial);
                  }}
                >
                  Cancelar edicion
                </button>
              ) : (
                <span />
              )}
              <button type="submit">{categoriaEditando ? 'Actualizar categoria' : 'Guardar categoria'}</button>
            </div>
          </form>

          <div className="inventory-admin-list">
            {categorias.map((categoria) => (
              <article key={categoria.id} className="inventory-admin-item">
                <div>
                  <strong>{categoria.nombre}</strong>
                  <p>{categoria.descripcion || 'Sin descripcion'}</p>
                </div>
                <div className="inventory-admin-actions">
                  <button type="button" className="secondary compact" onClick={() => editarCategoria(categoria)}>
                    Editar
                  </button>
                  <button
                    type="button"
                    className="secondary compact inventory-danger-soft"
                    onClick={() => eliminarCategoria(categoria)}
                  >
                    Eliminar
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </Modal>

      <Modal
        open={modalMarcaOpen}
        onClose={() => setModalMarcaOpen(false)}
        title="CRUD de marcas"
        subtitle="Gestiona las marcas maestras que usan productos y compras."
        size="lg"
      >
        <div className="inventory-admin-modal">
          <form className="entity-form" onSubmit={guardarMarca}>
            <div className="form-grid two-columns">
              <label>
                <span>Nombre</span>
                <input
                  value={marcaForm.nombre}
                  onChange={(event) => setMarcaForm((actual) => ({ ...actual, nombre: event.target.value }))}
                  required
                />
              </label>
              <label>
                <span>Descripcion</span>
                <input
                  value={marcaForm.descripcion}
                  onChange={(event) => setMarcaForm((actual) => ({ ...actual, descripcion: event.target.value }))}
                />
              </label>
            </div>
            <div className="modal-actions-row">
              {marcaEditando ? (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setMarcaEditando(null);
                    setMarcaForm(marcaInicial);
                  }}
                >
                  Cancelar edicion
                </button>
              ) : (
                <span />
              )}
              <button type="submit">{marcaEditando ? 'Actualizar marca' : 'Guardar marca'}</button>
            </div>
          </form>

          <div className="inventory-admin-list">
            {marcas.map((marca) => (
              <article key={marca.id} className="inventory-admin-item">
                <div>
                  <strong>{marca.nombre}</strong>
                  <p>{marca.descripcion || 'Sin descripcion'}</p>
                </div>
                <div className="inventory-admin-actions">
                  <button type="button" className="secondary compact" onClick={() => editarMarca(marca)}>
                    Editar
                  </button>
                  <button
                    type="button"
                    className="secondary compact inventory-danger-soft"
                    onClick={() => eliminarMarca(marca)}
                  >
                    Eliminar
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </Modal>

      <Modal
        open={modalProductoOpen}
        onClose={cerrarModalProducto}
        title={productoEditando ? 'Editar producto' : 'Nuevo producto'}
        subtitle="Registra la ficha base del inventario usando categoria y marca maestras."
        size="lg"
      >
        <form className="entity-form" onSubmit={guardarProducto}>
          <div className="form-grid two-columns">
            <label>
              <span>Categoria</span>
              <select
                value={productoForm.categoriaId}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, categoriaId: event.target.value }))}
                required
              >
                <option value="">Selecciona una categoria</option>
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
                value={productoForm.marcaId}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, marcaId: event.target.value }))}
                required
              >
                <option value="">Selecciona una marca</option>
                {marcas.map((marca) => (
                  <option key={marca.id} value={marca.id}>
                    {marca.nombre}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>SKU</span>
              <input
                value={productoForm.sku}
                onChange={(event) => {
                  setSkuTocado(true);
                  setProductoForm((actual) => ({ ...actual, sku: event.target.value }));
                }}
                required
                readOnly={skuNoEditable}
                disabled={skuNoEditable}
                placeholder="BAT-SAM-A04-ORI"
              />
            </label>
            <label>
              <span>Nombre</span>
              <input
                value={productoForm.nombre}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, nombre: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Calidad</span>
              <input
                value={productoForm.calidad}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, calidad: event.target.value }))}
                placeholder="Original, Incell, OLED..."
              />
            </label>
            <label>
              <span>Precio de venta</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={productoForm.precioVenta}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, precioVenta: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Costo de compra</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={productoForm.costoUnitario}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, costoUnitario: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Stock minimo</span>
              <input
                type="number"
                min="0"
                step="1"
                value={productoForm.stockMinimo}
                onChange={(event) => setProductoForm((actual) => ({ ...actual, stockMinimo: event.target.value }))}
                required
              />
            </label>
            {!productoEditando && (
              <label>
                <span>Stock inicial</span>
                <input
                  type="number"
                  min="0"
                  step="1"
                  value={productoForm.stockInicial}
                  onChange={(event) => setProductoForm((actual) => ({ ...actual, stockInicial: event.target.value }))}
                />
              </label>
            )}
          </div>
          <label>
            <span>Descripcion</span>
            <textarea
              value={productoForm.descripcion}
              onChange={(event) => setProductoForm((actual) => ({ ...actual, descripcion: event.target.value }))}
            />
          </label>
          <div className="alert inventory-alert-detailed">
            <div className="inventory-alert-copy">
              <strong>SKU sugerido: {skuSugerido || 'Completa categoria, marca y modelo'}</strong>
              <p>
                {skuNoEditable
                  ? 'El SKU queda bloqueado porque este producto ya tiene historial operativo.'
                  : `Se guardara como: ${skuNormalizadoPreview || 'sin valor'}${skuInvalido ? ' · formato invalido' : ''}`}
              </p>
            </div>
            {!skuNoEditable && skuSugerido && skuSugerido !== productoForm.sku && (
              <button
                type="button"
                className="secondary compact"
                onClick={() => {
                  setSkuTocado(true);
                  setProductoForm((actual) => ({ ...actual, sku: skuSugerido }));
                }}
              >
                Usar sugerencia
              </button>
            )}
          </div>
          {cargandoSku && <div className="alert">Validando SKU y buscando duplicados parecidos...</div>}
          {productosSimilares.length > 0 && (
            <div className="alert inventory-alert-detailed">
              <div className="inventory-alert-copy">
                <strong>Productos parecidos detectados</strong>
                <p>
                  Ya existen {productosSimilares.length} registro{productosSimilares.length === 1 ? '' : 's'} con la misma categoria, marca, nombre/modelo y calidad.
                </p>
                <p>
                  {productosSimilares
                    .slice(0, 3)
                    .map((item) => `${item.sku} · ${item.nombre}`)
                    .join(' | ')}
                </p>
              </div>
            </div>
          )}
          <div className="alert">
            Regla operativa: si es el mismo producto maestro reutiliza el SKU; si cambia calidad, modelo o compatibilidad real, crea un SKU nuevo.
          </div>
          {!productoEditando && (
            <div className="alert">
              Si registras stock inicial aqui, el sistema lo guardara como un movimiento real del kardex y no como edicion directa.
            </div>
          )}
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={cerrarModalProducto}>
              Cancelar
            </button>
            <button type="submit">{productoEditando ? 'Actualizar producto' : 'Guardar producto'}</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={modalAjusteOpen}
        onClose={() => setModalAjusteOpen(false)}
        title={`Ajuste de inventario${productoSeleccionado ? ` · ${productoSeleccionado.nombre}` : ''}`}
        subtitle="Usa este ajuste solo para correcciones manuales fuera de compras o ventas."
      >
        <form className="entity-form" onSubmit={guardarAjuste}>
          <div className="form-grid two-columns">
            <label>
              <span>Tipo de movimiento</span>
              <select
                value={ajusteForm.tipoMovimiento}
                onChange={(event) => setAjusteForm((actual) => ({ ...actual, tipoMovimiento: event.target.value }))}
              >
                <option value="ENTRADA">Entrada</option>
                <option value="SALIDA">Salida</option>
              </select>
            </label>
            <label>
              <span>Cantidad</span>
              <input
                type="number"
                min="1"
                value={ajusteForm.cantidad}
                onChange={(event) => setAjusteForm((actual) => ({ ...actual, cantidad: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Costo unitario</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={ajusteForm.costoUnitario}
                onChange={(event) => setAjusteForm((actual) => ({ ...actual, costoUnitario: event.target.value }))}
              />
            </label>
          </div>
          <label>
            <span>Descripcion</span>
            <textarea
              value={ajusteForm.descripcion}
              onChange={(event) => setAjusteForm((actual) => ({ ...actual, descripcion: event.target.value }))}
              required
            />
          </label>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalAjusteOpen(false)}>
              Cancelar
            </button>
            <button type="submit">Guardar ajuste</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
