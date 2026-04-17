import { useMemo, useState } from 'react';
import { Search, X, Save, Plus, Trash2, ChevronRight, Funnel } from 'lucide-react';
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
}) {
  const [productQuery, setProductQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [showCategoryFilters, setShowCategoryFilters] = useState(false);

  const productCategories = useMemo(() => {
    const categoriesMap = new Map();
    products.forEach((product) => {
      const id = product?.categoria?.id;
      const nombre = product?.categoria?.nombre;
      if (!id || !nombre || categoriesMap.has(String(id))) return;
      categoriesMap.set(String(id), { id: String(id), nombre });
    });
    return Array.from(categoriesMap.values()).sort((a, b) => a.nombre.localeCompare(b.nombre));
  }, [products]);

  const filteredProducts = useMemo(() => {
    const term = productQuery.trim().toLowerCase();

    return products.filter((product) => {
      const matchesCategory = !selectedCategory || String(product?.categoria?.id) === String(selectedCategory);
      if (!matchesCategory) return false;
      if (!term) return true;

      return [
        product?.nombre,
        product?.sku,
        product?.descripcion,
        product?.categoria?.nombre,
      ].some((value) => String(value || '').toLowerCase().includes(term));
    });
  }, [products, productQuery, selectedCategory]);

  const selectedProduct = useMemo(
    () => products.find((product) => String(product.id) === String(selectedPart.productoId)),
    [products, selectedPart.productoId],
  );

  const handleSelectProduct = (product) => {
    setSelectedPart({
      ...selectedPart,
      productoId: String(product.id),
      nombreParte: selectedPart.nombreParte || product.nombre,
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
              <label className="repair-field-label">Cliente</label>

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
                        <div className="repair-result-card__icon">👤</div>
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
                  <div className="repair-selected-card__icon">👤</div>
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
              <label className="repair-field-label">Dispositivo</label>

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
                        <div className="repair-result-card__icon">📱</div>
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
                  <div className="repair-selected-card__icon">📱</div>
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
            <div className="repair-grid repair-grid--parts">
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
                  {(productQuery || selectedPart.productoId || selectedCategory) && (
                    <button
                      type="button"
                      className="repair-search-clear"
                      onClick={handleClearProduct}
                    >
                      <X size={14} />
                    </button>
                  )}
                </div>

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

                {(productQuery.trim() || selectedCategory) && (
                  <div className="repair-search-results repair-search-results--products">
                    {filteredProducts.length === 0 ? (
                      <div className="repair-search-empty">No se encontraron productos</div>
                    ) : (
                      filteredProducts.map((product) => (
                        <button
                          type="button"
                          key={product.id}
                          className={`repair-result-card ${String(selectedPart.productoId) === String(product.id) ? 'active' : ''}`}
                          onClick={() => handleSelectProduct(product)}
                        >
                          <div className="repair-result-card__icon">📦</div>
                          <div className="repair-result-card__content">
                            <strong>{product.nombre}</strong>
                            <span>{product.categoria?.nombre || 'Sin categoria'} · Stock {product.cantidadStock}</span>
                          </div>
                        </button>
                      ))
                    )}
                  </div>
                )}

                {selectedProduct && !productQuery.trim() && !selectedCategory && (
                  <div className="repair-selected-card">
                    <div className="repair-selected-card__icon">📦</div>
                    <div className="repair-selected-card__content">
                      <strong>{selectedProduct.nombre}</strong>
                      <span>{selectedProduct.categoria?.nombre || 'Sin categoria'} · Stock {selectedProduct.cantidadStock}</span>
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
              </label>

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
