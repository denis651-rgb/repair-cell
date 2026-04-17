import Modal from '../common/Modal';

export default function StockAdjustmentModal({ open, onClose, onSubmit, form, setForm, product }) {
  return (
    <Modal open={open} onClose={onClose} title="Ajustar stock" subtitle={product ? `Producto: ${product.nombre}` : ''}>
      <form className="entity-form" onSubmit={onSubmit}>
        <div className="form-grid two-columns">
          <label>
            <span>Tipo movimiento</span>
            <select value={form.tipoMovimiento} onChange={(e) => setForm({ ...form, tipoMovimiento: e.target.value })}>
              <option value="ENTRADA">Entrada</option>
              <option value="SALIDA">Salida</option>
            </select>
          </label>
          <label>
            <span>Cantidad</span>
            <input type="number" min="1" value={form.cantidad} onChange={(e) => setForm({ ...form, cantidad: e.target.value })} required />
          </label>
          <label>
            <span>Costo unitario</span>
            <input type="number" min="0" step="0.01" value={form.costoUnitario} onChange={(e) => setForm({ ...form, costoUnitario: e.target.value })} />
          </label>
        </div>
        <label>
          <span>Descripción</span>
          <textarea value={form.descripcion} onChange={(e) => setForm({ ...form, descripcion: e.target.value })} maxLength={250} />
        </label>
        <div className="modal-actions-row">
          <button type="button" className="secondary" onClick={onClose}>Cancelar</button>
          <button type="submit">Aplicar ajuste</button>
        </div>
      </form>
    </Modal>
  );
}
