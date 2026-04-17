import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowDownToLine,
  ArrowUpToLine,
  Boxes,
  PackageSearch,
  Plus,
  Search,
  Tags,
  WalletCards,
} from 'lucide-react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import StockAdjustmentModal from '../components/modals/StockAdjustmentModal';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import '../styles/pages/inventario.css';

const PAGE_SIZE = 8;
const MOVEMENTS_PAGE_SIZE = 6;
const initialPage = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const categoriaInitial = { nombre: '', descripcion: '' };
const productInitial = { categoriaId: '', sku: '', nombre: '', descripcion: '', costoUnitario: 0, precioVenta: 0, cantidadStock: 0, stockMinimo: 0, activo: true };
const adjustmentInitial = { cantidad: 1, tipoMovimiento: 'ENTRADA', descripcion: '', costoUnitario: '' };

export default function InventoryPage() {
  const [categories, setCategories] = useState([]);
  const [productsPage, setProductsPage] = useState(initialPage);
  const [movementsPage, setMovementsPage] = useState(initialPage);
  const [lowStockProducts, setLowStockProducts] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [productsPageIndex, setProductsPageIndex] = useState(0);
  const [movementsPageIndex, setMovementsPageIndex] = useState(0);
  const [categoryForm, setCategoryForm] = useState(categoriaInitial);
  const [productForm, setProductForm] = useState(productInitial);
  const [adjustmentForm, setAdjustmentForm] = useState(adjustmentInitial);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [error, setError] = useState('');
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [productModalOpen, setProductModalOpen] = useState(false);
  const [adjustmentModalOpen, setAdjustmentModalOpen] = useState(false);
  const productsSectionRef = useRef(null);
  const debouncedSearch = useDebouncedValue(searchTerm, 250);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const loadStaticInventory = async () => {
    try {
      setError('');
      const [categoriesData, lowStockData] = await Promise.all([
        api.get('/inventario/categorias'),
        api.get('/inventario/productos/stock-bajo'),
      ]);
      setCategories(categoriesData || []);
      setLowStockProducts(lowStockData || []);
    } catch (err) {
      setError(err.message);
    }
  };

  const loadProducts = async (pagina = productsPageIndex, busqueda = debouncedSearch) => {
    try {
      setError('');
      setProductsPage((await api.get('/inventario/productos/paginado', {
        pagina,
        tamano: PAGE_SIZE,
        busqueda,
      })) || initialPage);
    } catch (err) {
      setError(err.message);
    }
  };

  const loadMovements = async (pagina = movementsPageIndex) => {
    try {
      setError('');
      setMovementsPage((await api.get('/inventario/movimientos/paginado', {
        pagina,
        tamano: MOVEMENTS_PAGE_SIZE,
      })) || initialPage);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadStaticInventory();
  }, []);

  useEffect(() => {
    loadProducts(productsPageIndex, debouncedSearch);
  }, [productsPageIndex, debouncedSearch]);

  useEffect(() => {
    loadMovements(movementsPageIndex);
  }, [movementsPageIndex]);

  useEffect(() => {
    setProductsPageIndex(0);
  }, [debouncedSearch]);

  const products = productsPage.content || [];
  const movements = movementsPage.content || [];

  const totalInventoryValue = useMemo(
    () => products.reduce((sum, product) => sum + (Number(product.cantidadStock || 0) * Number(product.precioVenta || 0)), 0),
    [products],
  );

  const totalUnits = useMemo(
    () => products.reduce((sum, product) => sum + Number(product.cantidadStock || 0), 0),
    [products],
  );

  const saveCategory = async (event) => {
    event.preventDefault();
    try {
      await api.post('/inventario/categorias', categoryForm);
      setCategoryForm(categoriaInitial);
      setCategoryModalOpen(false);
      await loadStaticInventory();
    } catch (err) {
      setError(err.message);
    }
  };

  const saveProduct = async (event) => {
    event.preventDefault();
    try {
      await api.post('/inventario/productos', {
        sku: productForm.sku,
        nombre: productForm.nombre,
        descripcion: productForm.descripcion,
        costoUnitario: Number(productForm.costoUnitario),
        precioVenta: Number(productForm.precioVenta),
        cantidadStock: Number(productForm.cantidadStock),
        stockMinimo: Number(productForm.stockMinimo),
        activo: true,
      }, { categoriaId: Number(productForm.categoriaId) });
      setProductForm(productInitial);
      setProductModalOpen(false);
      await Promise.all([loadProducts(productsPageIndex, debouncedSearch), loadStaticInventory()]);
    } catch (err) {
      setError(err.message);
    }
  };

  const openAdjustment = (product, tipoMovimiento) => {
    setSelectedProduct(product);
    setAdjustmentForm({ ...adjustmentInitial, tipoMovimiento });
    setAdjustmentModalOpen(true);
  };

  const submitAdjustment = async (event) => {
    event.preventDefault();
    try {
      await api.post(`/inventario/productos/${selectedProduct.id}/stock`, {
        cantidad: Number(adjustmentForm.cantidad),
        tipoMovimiento: adjustmentForm.tipoMovimiento,
        descripcion: adjustmentForm.descripcion,
        costoUnitario: adjustmentForm.costoUnitario === '' ? null : Number(adjustmentForm.costoUnitario),
      });
      setAdjustmentModalOpen(false);
      setAdjustmentForm(adjustmentInitial);
      setSelectedProduct(null);
      await Promise.all([
        loadProducts(productsPageIndex, debouncedSearch),
        loadMovements(0),
        loadStaticInventory(),
      ]);
      setMovementsPageIndex(0);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleReviewLowStock = () => {
    productsSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const getProductInitials = (name = '') =>
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('') || 'PR';

  const getStockRatio = (product) => {
    const stock = Number(product.cantidadStock || 0);
    const minimum = Math.max(Number(product.stockMinimo || 0), 1);
    return Math.min(100, Math.round((stock / minimum) * 100));
  };

  const formatMovementMeta = (movement) => {
    const dateValue = movement.fechaMovimiento || movement.fecha || movement.createdAt || movement.fechaCreacion;
    if (!dateValue) return 'Movimiento reciente';

    const parsed = new Date(dateValue);
    if (Number.isNaN(parsed.getTime())) return 'Movimiento reciente';

    return parsed.toLocaleString('es-BO', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="page-stack inventory-page">
      <PageHeader title="Inventario" subtitle="Control de suministros, repuestos y movimientos del taller.">
        <div className="inventory-header-actions">
          <button className="secondary inventory-ghost-button" onClick={() => setCategoryModalOpen(true)}>
            <Tags size={18} />
            Categoría
          </button>
          <button className="inventory-primary-button" onClick={() => setProductModalOpen(true)}>
            <Plus size={18} />
            Producto
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="inventory-hero-card">
        <div className="inventory-toolbar">
          <label className="inventory-search">
            <Search size={18} />
            <input
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="Buscar productos, SKU o categoría..."
            />
          </label>
          <div className="inventory-toolbar-note">
            <span className="inventory-dot" />
            {productsPage.totalElements} resultados
          </div>
        </div>
      </section>

      {lowStockProducts.length > 0 && (
        <div className="low-stock-banner">
          <div className="low-stock-icon">
            <AlertTriangle size={24} />
          </div>
          <div className="low-stock-copy">
            <strong>Alerta de existencias</strong>
            <p>
              Hay {lowStockProducts.length} producto{lowStockProducts.length === 1 ? '' : 's'} con stock bajo.
              Recomendamos reponer inventario pronto.
            </p>
          </div>
          <button type="button" className="low-stock-action" onClick={handleReviewLowStock}>
            Ver detalles
          </button>
        </div>
      )}

      <section className="inventory-kpi-grid">
        <article className="inventory-kpi-card">
          <div className="inventory-kpi-icon icon-soft">
            <Boxes size={20} />
          </div>
          <span className="inventory-kpi-label">Total productos</span>
          <strong className="inventory-kpi-value">{productsPage.totalElements}</strong>
          <p>{categories.length} categorías activas</p>
        </article>

        <article className="inventory-kpi-card">
          <div className="inventory-kpi-icon icon-danger">
            <AlertTriangle size={20} />
          </div>
          <span className="inventory-kpi-label">Stock bajo</span>
          <strong className="inventory-kpi-value inventory-kpi-value-danger">{String(lowStockProducts.length).padStart(2, '0')}</strong>
          <p>{lowStockProducts.length > 0 ? 'Requiere atención' : 'Todo estable'}</p>
        </article>

        <article className="inventory-kpi-card">
          <div className="inventory-kpi-icon icon-warning">
            <WalletCards size={20} />
          </div>
          <span className="inventory-kpi-label">Valor visible</span>
          <strong className="inventory-kpi-value">Bs {currency.format(totalInventoryValue)}</strong>
          <p>Estimado en la página actual</p>
        </article>

        <article className="inventory-kpi-card inventory-kpi-card-accent">
          <div className="inventory-kpi-icon icon-soft">
            <PackageSearch size={20} />
          </div>
          <span className="inventory-kpi-label">Unidades visibles</span>
          <strong className="inventory-kpi-value">{totalUnits}</strong>
          <p>{movementsPage.totalElements} movimientos registrados</p>
        </article>
      </section>

      <section className="inventory-layout">
        <div className="inventory-main-panel card" ref={productsSectionRef}>
          <div className="inventory-panel-header">
            <div>
              <h3>Lista de productos</h3>
              <p>{debouncedSearch.trim() ? 'Resultados paginados según la búsqueda actual' : 'Vista general paginada del inventario actual'}</p>
            </div>
            <div className="inventory-panel-summary">
              <span className="chip">{productsPage.totalElements} productos</span>
            </div>
          </div>

          {products.length === 0 ? (
            <EmptyState
              title={productsPage.totalElements === 0 ? 'No hay productos todavía' : 'No encontramos coincidencias'}
              description={productsPage.totalElements === 0 ? 'Crea el primer producto desde el botón superior.' : 'Prueba con otro nombre, SKU o categoría.'}
            />
          ) : (
            <>
              <div className="inventory-products-table">
                <div className="inventory-products-head">
                  <span>Producto</span>
                  <span>Categoría</span>
                  <span>Stock</span>
                  <span>Precio (Bs)</span>
                  <span>Acciones</span>
                </div>

                <div className="inventory-products-list">
                  {products.map((product) => {
                    const isLowStock = Number(product.cantidadStock) <= Number(product.stockMinimo);
                    const stockRatio = getStockRatio(product);

                    return (
                      <article key={product.id} className="inventory-product-row">
                        <div className="inventory-product-cell inventory-product-info">
                          <div className="inventory-product-avatar">{getProductInitials(product.nombre)}</div>
                          <div>
                            <strong>{product.nombre}</strong>
                            <p>{product.descripcion || product.sku || 'Sin descripción adicional'}</p>
                          </div>
                        </div>

                        <div className="inventory-product-cell">
                          <span className="inventory-category-pill">{product.categoria?.nombre || 'Sin categoría'}</span>
                        </div>

                        <div className="inventory-product-cell">
                          <div className="inventory-stock-block">
                            <strong className={isLowStock ? 'inventory-stock-danger' : ''}>
                              {product.cantidadStock} <span>/ mín. {product.stockMinimo}</span>
                            </strong>
                            <div className="inventory-stock-track">
                              <div
                                className={`inventory-stock-fill ${isLowStock ? 'is-danger' : ''}`}
                                style={{ width: `${stockRatio}%` }}
                              />
                            </div>
                          </div>
                        </div>

                        <div className="inventory-product-cell inventory-price-cell">
                          Bs {currency.format(Number(product.precioVenta || 0))}
                        </div>

                        <div className="inventory-product-cell">
                          <div className="inventory-actions-inline">
                            <button className="secondary button-inline" onClick={() => openAdjustment(product, 'ENTRADA')}>
                              <ArrowUpToLine size={16} />
                              Entrada
                            </button>
                            <button className="secondary button-inline inventory-danger-soft" onClick={() => openAdjustment(product, 'SALIDA')}>
                              <ArrowDownToLine size={16} />
                              Salida
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
                  disabled={productsPageIndex <= 0}
                  onClick={() => setProductsPageIndex((current) => Math.max(current - 1, 0))}
                >
                  Anterior
                </button>

                <span>
                  Página {productsPage.number + 1} de {Math.max(productsPage.totalPages || 1, 1)}
                </span>

                <button
                  className="secondary"
                  type="button"
                  disabled={productsPageIndex + 1 >= (productsPage.totalPages || 1)}
                  onClick={() => setProductsPageIndex((current) => current + 1)}
                >
                  Siguiente
                </button>
              </div>
            </>
          )}
        </div>

        <aside className="inventory-side-column">
          <div className="inventory-side-card inventory-movements-card">
            <div className="inventory-panel-header">
              <div>
                <h3>Movimientos recientes</h3>
                <p>Últimos ajustes registrados en inventario</p>
              </div>
              <span className="text-link">{movementsPage.totalElements}</span>
            </div>

            {movements.length === 0 ? (
              <EmptyState title="Sin movimientos aún" description="Los ajustes manuales aparecerán aquí." />
            ) : (
              <>
                <div className="inventory-movements-list">
                  {movements.map((movement) => {
                    const isEntry = movement.tipoMovimiento === 'ENTRADA';

                    return (
                      <article key={movement.id} className="inventory-movement-item">
                        <div className={`inventory-movement-icon ${isEntry ? 'is-entry' : 'is-exit'}`}>
                          {isEntry ? <ArrowUpToLine size={16} /> : <ArrowDownToLine size={16} />}
                        </div>
                        <div className="inventory-movement-copy">
                          <strong>
                            {isEntry ? 'Entrada' : 'Salida'}: {movement.producto?.nombre || `Producto #${movement.productoId || '—'}`}
                          </strong>
                          <p>
                            {isEntry ? '+' : '-'}
                            {movement.cantidad} unidades
                            {movement.descripcion ? ` • ${movement.descripcion}` : ''}
                          </p>
                          <span>{formatMovementMeta(movement)}</span>
                        </div>
                      </article>
                    );
                  })}
                </div>

                <div className="pagination-row inventory-side-pagination-row">
                  <button
                    className="secondary"
                    type="button"
                    disabled={movementsPageIndex <= 0}
                    onClick={() => setMovementsPageIndex((current) => Math.max(current - 1, 0))}
                  >
                    Anterior
                  </button>

                  <span>
                    Página {movementsPage.number + 1} de {Math.max(movementsPage.totalPages || 1, 1)}
                  </span>

                  <button
                    className="secondary"
                    type="button"
                    disabled={movementsPageIndex + 1 >= (movementsPage.totalPages || 1)}
                    onClick={() => setMovementsPageIndex((current) => current + 1)}
                  >
                    Siguiente
                  </button>
                </div>
              </>
            )}
          </div>

          <div className="inventory-side-card inventory-cta-card">
            <span className="inventory-cta-kicker">Gestión rápida</span>
            <h3>¿Necesitas registrar algo al instante?</h3>
            <p>Usa los botones de entrada o salida de cada producto para mantener el stock siempre actualizado.</p>
            <button type="button" onClick={() => setProductModalOpen(true)}>
              Abrir alta de producto
            </button>
          </div>
        </aside>
      </section>

      <Modal open={categoryModalOpen} onClose={() => setCategoryModalOpen(false)} title="Nueva categoría" subtitle="Mantén el inventario organizado por familias de productos.">
        <form className="entity-form" onSubmit={saveCategory}>
          <label>
            <span>Nombre</span>
            <input value={categoryForm.nombre} onChange={(event) => setCategoryForm({ ...categoryForm, nombre: event.target.value })} required />
          </label>
          <label>
            <span>Descripción</span>
            <textarea value={categoryForm.descripcion} onChange={(event) => setCategoryForm({ ...categoryForm, descripcion: event.target.value })} />
          </label>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setCategoryModalOpen(false)}>Cancelar</button>
            <button type="submit">Guardar categoría</button>
          </div>
        </form>
      </Modal>

      <Modal open={productModalOpen} onClose={() => setProductModalOpen(false)} title="Nuevo producto" subtitle="Se guarda usando el contrato actual del backend con categoriaId en query param." size="lg">
        <form className="entity-form" onSubmit={saveProduct}>
          <div className="form-grid two-columns">
            <label>
              <span>Categoría</span>
              <select value={productForm.categoriaId} onChange={(event) => setProductForm({ ...productForm, categoriaId: event.target.value })} required>
                <option value="">Selecciona categoría</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.nombre}</option>)}
              </select>
            </label>
            <label>
              <span>SKU</span>
              <input value={productForm.sku} onChange={(event) => setProductForm({ ...productForm, sku: event.target.value })} required />
            </label>
            <label>
              <span>Nombre</span>
              <input value={productForm.nombre} onChange={(event) => setProductForm({ ...productForm, nombre: event.target.value })} required />
            </label>
            <label>
              <span>Precio de venta</span>
              <input type="number" min="0" step="0.01" value={productForm.precioVenta} onChange={(event) => setProductForm({ ...productForm, precioVenta: event.target.value })} required />
            </label>
            <label>
              <span>Costo unitario</span>
              <input type="number" min="0" step="0.01" value={productForm.costoUnitario} onChange={(event) => setProductForm({ ...productForm, costoUnitario: event.target.value })} required />
            </label>
            <label>
              <span>Stock inicial</span>
              <input type="number" min="0" step="1" value={productForm.cantidadStock} onChange={(event) => setProductForm({ ...productForm, cantidadStock: event.target.value })} required />
            </label>
            <label>
              <span>Stock mínimo</span>
              <input type="number" min="0" step="1" value={productForm.stockMinimo} onChange={(event) => setProductForm({ ...productForm, stockMinimo: event.target.value })} required />
            </label>
          </div>
          <label>
            <span>Descripción</span>
            <textarea value={productForm.descripcion} onChange={(event) => setProductForm({ ...productForm, descripcion: event.target.value })} />
          </label>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setProductModalOpen(false)}>Cancelar</button>
            <button type="submit">Guardar producto</button>
          </div>
        </form>
      </Modal>

      <StockAdjustmentModal
        open={adjustmentModalOpen}
        onClose={() => setAdjustmentModalOpen(false)}
        onSubmit={submitAdjustment}
        form={adjustmentForm}
        setForm={setAdjustmentForm}
        product={selectedProduct}
      />
    </div>
  );
}
