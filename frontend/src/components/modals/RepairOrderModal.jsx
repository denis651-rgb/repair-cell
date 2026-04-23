import { useMemo, useState } from 'react';
import {
  Search,
  X,
  Save,
  Plus,
  Trash2,
  ChevronRight,
  Funnel,
  UserRoundPlus,
  SmartphoneNfc,
  UserRound,
  Smartphone,
  Package,
} from 'lucide-react';
import Modal from '../common/Modal';

const initialPart = { productoId: '', nombreParte: '', cantidad: 1, tipoFuente: 'TIENDA' };

function SectionBadge({ number }) {
  return <div className="repair-modal-section-badge">{number}</div>;
}

export default function RepairOrderModal({
  open,
  onClose,
  onSubmit,
  onSaveDraft,
  form,
  setForm,
  clientes,
  dispositivos,
  products,
  selectedPart,
  setSelectedPart,
  addPart,
  removePart,
  loading,
  clientQuery,
  setClientQuery,
  deviceQuery,
  setDeviceQuery,
  selectedClientLabel,
  selectedDeviceLabel,
  onSelectClient,
  onSelectDevice,
  onClearClient,
  onClearDevice,
  quickClientOpen,
  setQuickClientOpen,
  quickDeviceOpen,
  setQuickDeviceOpen,
  quickClientForm,
  setQuickClientForm,
  quickDeviceForm,
  setQuickDeviceForm,
  onQuickCreateClient,
  onQuickCreateDevice,
  quickClientLoading,
  quickDeviceLoading,
}) {
  const [productQuery, setProductQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [showCategoryFilters, setShowCategoryFilters] = useState(false);

  const productCategories = useMemo(() => {
    const categoriesMap = new Map();
    products.forEach((product) => {
      const id = product?.categoria?.id || product?.categoriaId || product?.categoriaNombre;
      const nombre = product?.categoria?.nombre || product?.categoriaNombre;
      if (!id || !nombre || categoriesMap.has(String(id))) return;
      categoriesMap.set(String(id), { id: String(id), nombre });
    });
    return Array.from(categoriesMap.values()).sort((a, b) => a.nombre.localeCompare(b.nombre));
  }, [products]);

  const filteredProducts = useMemo(() => {
    const termBusqueda = productQuery.trim().toLowerCase();
    const termParteLibre = selectedPart.nombreParte.trim().toLowerCase();
    const term = termBusqueda || termParteLibre;

    return products.filter((product) => {
      const categoriaId = product?.categoria?.id || product?.categoriaId || product?.categoriaNombre;
      const matchesCategory = !selectedCategory || String(categoriaId) === String(selectedCategory);
      if (!matchesCategory) return false;
      if (!term) return true;

      return [
        product?.nombre,
        product?.nombreBase,
        product?.codigoVariante,
        product?.marcaNombre,
        product?.modelo,
        product?.calidad,
        product?.sku,
        product?.descripcion,
        product?.categoria?.nombre,
        product?.categoriaNombre,
      ].some((value) => String(value || '').toLowerCase().includes(term));
    });
  }, [products, productQuery, selectedCategory, selectedPart.nombreParte]);

  const selectedProduct = useMemo(
    () => products.find((product) => String(product.varianteId || product.id) === String(selectedPart.varianteId || selectedPart.productoId)),
    [products, selectedPart.productoId, selectedPart.varianteId],
  );

  const handleSelectProduct = (product) => {
    setSelectedPart({
      ...selectedPart,
      productoId: '',
      varianteId: String(product.varianteId || product.id),
      nombreParte: selectedPart.nombreParte || product.nombreBase || product.nombre,
    });
    setProductQuery('');
    setShowCategoryFilters(false);
  };

  const handleClearProduct = () => {
    setProductQuery('');
    setSelectedCategory('');
    setShowCategoryFilters(false);
    setSelectedPart({
      ...selectedPart,
      productoId: '',
      varianteId: '',
    });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Crear nueva orden de reparación"
      subtitle="Inicia una ficha técnica y registra el progreso del dispositivo."
      size="xl"
      className="repair-order-visual-modal"
    >
      <form className="repair-visual-form" onSubmit={onSubmit}>
        <section className="repair-modal-block">
          <div className="repair-modal-block__title">
            <SectionBadge number="1" />
            <div>
              <h3>Cliente & dispositivo</h3>
            </div>
          </div>

          <div className="repair-modal-card-area repair-modal-card-area--two">
            <div className="repair-selection-card">
              <div className="repair-field-label-row">
                <label className="repair-field-label">Cliente</label>
                <button
                  type="button"
                  className={`repair-inline-icon-btn ${quickClientOpen ? 'active' : ''}`}
                  onClick={() => {
                    setQuickClientOpen((current) => !current);
                    if (quickDeviceOpen) setQuickDeviceOpen(false);
                  }}
                  aria-label="Agregar cliente rapido"
                  title="Agregar cliente rapido"
                >
                  <UserRoundPlus size={15} />
                </button>
              </div>

              <div className="repair-search-box">
                <Search size={14} />
                <input
                  value={clientQuery}
                  onChange={(e) => setClientQuery(e.target.value)}
                  placeholder="Buscar por nombre, teléfono o correo"
                />
                {clientQuery && (
                  <button
                    type="button"
                    className="repair-search-clear"
                    onClick={onClearClient}
                  >
                    <X size={14} />
                  </button>
                )}
              </div>

              {quickClientOpen && (
                <div className="repair-quick-create-panel">
                  <div className="repair-quick-create-grid">
                    <label className="repair-field">
                      <span className="repair-field-label">Nombre</span>
                      <input
                        value={quickClientForm.nombreCompleto}
                        onChange={(e) => setQuickClientForm({ ...quickClientForm, nombreCompleto: e.target.value })}
                        placeholder="Nombre del cliente"
                      />
                    </label>
                    <label className="repair-field">
                      <span className="repair-field-label">Telefono</span>
                      <input
                        value={quickClientForm.telefono}
                        onChange={(e) => setQuickClientForm({ ...quickClientForm, telefono: e.target.value })}
                        placeholder="Telefono"
                      />
                    </label>
                  </div>
                  <div className="repair-quick-create-actions">
                    <button
                      type="button"
                      className="repair-secondary-btn"
                      onClick={() => setQuickClientOpen(false)}
                    >
                      Cancelar
                    </button>
                    <button
                      type="button"
                      className="repair-primary-mini-btn"
                      onClick={onQuickCreateClient}
                      disabled={quickClientLoading}
                    >
                      {quickClientLoading ? 'Guardando...' : 'Guardar cliente'}
                    </button>
                  </div>
                </div>
              )}

              {clientQuery.trim() && (
                <div className="repair-search-results">
                  {clientes.length === 0 ? (
                    <div className="repair-search-empty">No se encontraron clientes</div>
                  ) : (
                    clientes.map((cliente) => (
                      <button
                        type="button"
                        key={cliente.id}
                        className={`repair-result-card ${String(form.clienteId) === String(cliente.id) ? 'active' : ''}`}
                        onClick={() => onSelectClient(cliente)}
                      >
                        <div className="repair-result-card__icon">
                          <UserRound size={16} />
                        </div>
                        <div className="repair-result-card__content">
                          <strong>{cliente.nombreCompleto}</strong>
                          <span>{cliente.telefono || cliente.email || 'Sin contacto'}</span>
                        </div>
                      </button>
                    ))
                  )}
                </div>
              )}

              {selectedClientLabel && !clientQuery.trim() && (
                <div className="repair-selected-card">
                  <div className="repair-selected-card__icon">
                    <UserRound size={16} />
                  </div>
                  <div className="repair-selected-card__content">
                    <strong>{selectedClientLabel}</strong>
                    <span>Cliente seleccionado</span>
                  </div>
                  <button
                    type="button"
                    className="repair-selected-card__remove"
                    onClick={onClearClient}
                  >
                    <X size={14} />
                  </button>
                </div>
              )}
            </div>

            <div className="repair-selection-card">
              <div className="repair-field-label-row">
                <label className="repair-field-label">Dispositivo</label>
                <button
                  type="button"
                  className={`repair-inline-icon-btn ${quickDeviceOpen ? 'active' : ''}`}
                  onClick={() => {
                    setQuickDeviceOpen((current) => !current);
                    if (quickClientOpen) setQuickClientOpen(false);
                  }}
                  aria-label="Agregar dispositivo rapido"
                  title="Agregar dispositivo rapido"
                  disabled={!form.clienteId}
                >
                  <SmartphoneNfc size={15} />
                </button>
              </div>

              <div className="repair-search-box">
                <Search size={14} />
                <input
                  value={deviceQuery}
                  onChange={(e) => setDeviceQuery(e.target.value)}
                  placeholder={
                    form.clienteId
                      ? 'Buscar por marca, modelo o IMEI'
                      : 'Primero selecciona un cliente'
                  }
                  disabled={!form.clienteId}
                />
                {deviceQuery && (
                  <button
                    type="button"
                    className="repair-search-clear"
                    onClick={onClearDevice}
                  >
                    <X size={14} />
                  </button>
                )}
              </div>

              {quickDeviceOpen && form.clienteId && (
                <div className="repair-quick-create-panel">
                  <div className="repair-quick-create-grid">
                    <label className="repair-field">
                      <span className="repair-field-label">Marca</span>
                      <input
                        value={quickDeviceForm.marca}
                        onChange={(e) => setQuickDeviceForm({ ...quickDeviceForm, marca: e.target.value })}
                        placeholder="Marca"
                      />
                    </label>
                    <label className="repair-field">
                      <span className="repair-field-label">Modelo</span>
                      <input
                        value={quickDeviceForm.modelo}
                        onChange={(e) => setQuickDeviceForm({ ...quickDeviceForm, modelo: e.target.value })}
                        placeholder="Modelo"
                      />
                    </label>
                    <label className="repair-field repair-field--full">
                      <span className="repair-field-label">IMEI / serie</span>
                      <input
                        value={quickDeviceForm.imeiSerie}
                        onChange={(e) => setQuickDeviceForm({ ...quickDeviceForm, imeiSerie: e.target.value })}
                        placeholder="Opcional"
                      />
                    </label>
                  </div>
                  <div className="repair-quick-create-actions">
                    <button
                      type="button"
                      className="repair-secondary-btn"
                      onClick={() => setQuickDeviceOpen(false)}
                    >
                      Cancelar
                    </button>
                    <button
                      type="button"
                      className="repair-primary-mini-btn"
                      onClick={onQuickCreateDevice}
                      disabled={quickDeviceLoading}
                    >
                      {quickDeviceLoading ? 'Guardando...' : 'Guardar dispositivo'}
                    </button>
                  </div>
                </div>
              )}

              {form.clienteId && deviceQuery.trim() && (
                <div className="repair-search-results">
                  {dispositivos.length === 0 ? (
                    <div className="repair-search-empty">No hay dispositivos para este cliente</div>
                  ) : (
                    dispositivos.map((dispositivo) => (
                      <button
                        type="button"
                        key={dispositivo.id}
                        className={`repair-result-card ${String(form.dispositivoId) === String(dispositivo.id) ? 'active' : ''}`}
                        onClick={() => onSelectDevice(dispositivo)}
                      >
                        <div className="repair-result-card__icon">
                          <Smartphone size={16} />
                        </div>
                        <div className="repair-result-card__content">
                          <strong>{dispositivo.marca} {dispositivo.modelo}</strong>
                          <span>{dispositivo.imeiSerie || 'Sin IMEI / serie'}</span>
                        </div>
                      </button>
                    ))
                  )}
                </div>
              )}

              {selectedDeviceLabel && !deviceQuery.trim() && (
                <div className="repair-selected-card">
                  <div className="repair-selected-card__icon">
                    <Smartphone size={16} />
                  </div>
                  <div className="repair-selected-card__content">
                    <strong>{selectedDeviceLabel}</strong>
                    <span>Dispositivo seleccionado</span>
                  </div>
                  <button
                    type="button"
                    className="repair-selected-card__remove"
                    onClick={onClearDevice}
                  >
                    <X size={14} />
                  </button>
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="repair-modal-block">
          <div className="repair-modal-block__title">
            <SectionBadge number="2" />
            <div>
              <h3>Detalles técnicos</h3>
            </div>
          </div>

          <div className="repair-soft-panel">
            <div className="repair-grid repair-grid--four">
              <label className="repair-field">
                <span className="repair-field-label">Técnico responsable</span>
                <input
                  value={form.tecnicoResponsable}
                  readOnly
                  disabled
                  placeholder="Se llena desde la sesión"
                />
              </label>

              <label className="repair-field">
                <span className="repair-field-label">Fecha estimada</span>
                <input
                  type="date"
                  value={form.fechaEntregaEstimada}
                  onChange={(e) => setForm({ ...form, fechaEntregaEstimada: e.target.value })}
                />
              </label>

              <label className="repair-field">
                <span className="repair-field-label">Garantía (días)</span>
                <input
                  type="number"
                  min="0"
                  step="1"
                  value={form.diasGarantia}
                  onChange={(e) => setForm({ ...form, diasGarantia: e.target.value })}
                />
              </label>

              <label className="repair-field">
                <span className="repair-field-label">Costo estimado (Bs)</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.costoEstimado}
                  onChange={(e) => setForm({ ...form, costoEstimado: e.target.value })}
                />
              </label>
            </div>

            <div className="repair-grid repair-grid--two">
              <label className="repair-field">
                <span className="repair-field-label">Problema reportado</span>
                <textarea
                  value={form.problemaReportado}
                  onChange={(e) => setForm({ ...form, problemaReportado: e.target.value })}
                  placeholder="Describe la falla reportada por el cliente"
                  required
                  maxLength={600}
                />
              </label>

              <label className="repair-field">
                <span className="repair-field-label">Diagnóstico técnico</span>
                <textarea
                  value={form.diagnosticoTecnico}
                  onChange={(e) => setForm({ ...form, diagnosticoTecnico: e.target.value })}
                  placeholder="Registra el diagnóstico inicial"
                  maxLength={600}
                />
              </label>
            </div>

            <div className="repair-grid repair-grid--two">
              <label className="repair-field">
                <span className="repair-field-label">Firma del cliente</span>
                <input
                  value={form.nombreFirmaCliente}
                  onChange={(e) => setForm({ ...form, nombreFirmaCliente: e.target.value })}
                  maxLength={120}
                />
              </label>

              <label className="repair-field">
                <span className="repair-field-label">Texto de confirmación</span>
                <input
                  value={form.textoConfirmacion}
                  onChange={(e) => setForm({ ...form, textoConfirmacion: e.target.value })}
                  maxLength={240}
                />
              </label>
            </div>
          </div>
        </section>

        <section className="repair-modal-block">
          <div className="repair-modal-block__title">
            <SectionBadge number="3" />
            <div>
              <h3>Partes & repuestos</h3>
            </div>
          </div>

          <div className="repair-soft-panel">
            <div className="repair-parts-builder">
              <label className="repair-field">
                <span className="repair-field-label">Producto inventario</span>
                <div className="repair-search-box repair-search-box--product">
                  <Search size={14} />
                  <input
                    value={productQuery}
                    onChange={(e) => setProductQuery(e.target.value)}
                    placeholder="Buscar producto, SKU o categoria"
                  />
                  <button
                    type="button"
                    className={`repair-search-filter ${selectedCategory ? 'active' : ''}`}
                    onClick={() => setShowCategoryFilters((current) => !current)}
                    aria-label="Filtrar por categoria"
                    title="Filtrar por categoria"
                  >
                    <Funnel size={14} />
                  </button>
                  {(productQuery || selectedPart.productoId || selectedPart.varianteId || selectedCategory) && (
                    <button
                      type="button"
                      className="repair-search-clear"
                      onClick={handleClearProduct}
                    >
                      <X size={14} />
                    </button>
                  )}
                </div>
              </label>

              {showCategoryFilters && (
                <div className="repair-category-filters">
                  <button
                    type="button"
                    className={`repair-category-chip ${selectedCategory === '' ? 'active' : ''}`}
                    onClick={() => setSelectedCategory('')}
                  >
                    Todas
                  </button>
                  {productCategories.map((category) => (
                    <button
                      type="button"
                      key={category.id}
                      className={`repair-category-chip ${String(selectedCategory) === String(category.id) ? 'active' : ''}`}
                      onClick={() => setSelectedCategory(category.id)}
                    >
                      {category.nombre}
                    </button>
                  ))}
                </div>
              )}

              {(productQuery.trim() || selectedCategory || selectedPart.nombreParte.trim()) && (
                <div className="repair-product-list">
                  {filteredProducts.length === 0 ? (
                    <div className="repair-search-empty">No se encontraron productos</div>
                  ) : (
                    filteredProducts.map((product) => (
                      <button
                        type="button"
                        key={product.varianteId || product.id}
                        className={`repair-product-list-item ${String(selectedPart.varianteId || selectedPart.productoId) === String(product.varianteId || product.id) ? 'active' : ''}`}
                        onClick={() => handleSelectProduct(product)}
                      >
                        <span className="repair-product-list-main">
                          <strong>{product.nombreBase || product.nombre}</strong>
                          <small>{product.codigoVariante || product.sku || 'Sin codigo'}</small>
                        </span>
                        <span className="repair-product-list-meta">
                          {product.categoriaNombre || product.categoria?.nombre || 'Sin categoria'}
                        </span>
                        <span className="repair-product-list-stock">
                          Stock {product.stockDisponibleTotal ?? product.cantidadStock ?? 0}
                        </span>
                      </button>
                    ))
                  )}
                </div>
              )}

              {selectedProduct && !productQuery.trim() && !selectedCategory && (
                <div className="repair-selected-card">
                  <div className="repair-selected-card__icon">
                    <Package size={16} />
                  </div>
                  <div className="repair-selected-card__content">
                    <strong>{selectedProduct.nombreBase || selectedProduct.nombre}</strong>
                    <span>{selectedProduct.codigoVariante || selectedProduct.sku || 'Sin codigo'} · {selectedProduct.categoriaNombre || selectedProduct.categoria?.nombre || 'Sin categoria'} · Stock {selectedProduct.stockDisponibleTotal ?? selectedProduct.cantidadStock ?? 0}</span>
                  </div>
                  <button
                    type="button"
                    className="repair-selected-card__remove"
                    onClick={handleClearProduct}
                  >
                    <X size={14} />
                  </button>
                </div>
              )}

              <div className="repair-grid repair-grid--parts-form">
                <label className="repair-field">
                  <span className="repair-field-label">Nombre parte libre</span>
                  <input
                    value={selectedPart.nombreParte}
                    onChange={(e) => setSelectedPart({ ...selectedPart, nombreParte: e.target.value })}
                    maxLength={120}
                    placeholder="Ej. Pantalla OLED"
                  />
                </label>

                <label className="repair-field">
                  <span className="repair-field-label">Cantidad</span>
                  <input
                    type="number"
                    min="1"
                    value={selectedPart.cantidad}
                    onChange={(e) => setSelectedPart({ ...selectedPart, cantidad: e.target.value })}
                  />
                </label>

                <label className="repair-field">
                  <span className="repair-field-label">Fuente</span>
                  <select
                    value={selectedPart.tipoFuente}
                    onChange={(e) => setSelectedPart({ ...selectedPart, tipoFuente: e.target.value })}
                  >
                    <option value="TIENDA">Tienda</option>
                    <option value="CLIENTE">Cliente</option>
                    <option value="EXTERNO">Externo</option>
                  </select>
                </label>

                <div className="repair-part-add-wrap">
                  <button type="button" className="repair-primary-mini-btn" onClick={addPart}>
                    <Plus size={14} />
                    Añadir
                  </button>
                </div>
              </div>
            </div>

            <div className="repair-parts-table">
              <div className="repair-parts-table__head">
                <span>Parte / repuesto</span>
                <span>Cant.</span>
                <span>Fuente</span>
                <span>Acción</span>
              </div>

              {form.partes.length === 0 ? (
                <div className="repair-parts-empty">
                  No has agregado repuestos todavía.
                </div>
              ) : (
                form.partes.map((part, index) => (
                  <div className="repair-parts-row" key={`${part.productoId || part.nombreParte}-${index}`}>
                    <span>{part.nombreParte || `Producto #${part.productoId}`}</span>
                    <span>{part.cantidad}</span>
                    <span>{part.tipoFuente}</span>
                    <button
                      type="button"
                      className="repair-trash-btn"
                      onClick={() => removePart(index)}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </section>

        <div className="repair-modal-footer">
          <button type="button" className="repair-footer-link" onClick={onClose}>
            Cancelar
          </button>

          <div className="repair-modal-footer__actions">
            <button
              type="button"
              className="repair-secondary-btn"
              onClick={onSaveDraft}
              disabled={loading}
            >
              <Save size={14} />
              Guardar borrador
            </button>

            <button type="submit" className="repair-primary-btn" disabled={loading}>
              {loading ? 'Guardando...' : 'Crear orden'}
              {!loading && <ChevronRight size={16} />}
            </button>
          </div>
        </div>
      </form>
    </Modal>
  );
}
