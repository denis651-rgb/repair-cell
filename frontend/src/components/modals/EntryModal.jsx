import Modal from '../common/Modal';

export default function EntryModal({ open, onClose, onSubmit, form, setForm, loading }) {
  return (
    <Modal open={open} onClose={onClose} title="Nuevo movimiento contable" subtitle="Registra ingresos o egresos y asígnalos a la fecha correcta del turno.">
      <form className="entity-form" onSubmit={onSubmit}>
        <div className="form-grid two-columns">
          <label>
            <span>Tipo</span>
            <select value={form.tipoEntrada} onChange={(event) => setForm({ ...form, tipoEntrada: event.target.value })}>
              <option value="ENTRADA">Entrada</option>
              <option value="SALIDA">Salida</option>
            </select>
          </label>
          <label>
            <span>Fecha</span>
            <input type="date" value={form.fechaEntrada} onChange={(event) => setForm({ ...form, fechaEntrada: event.target.value })} required />
          </label>
          <label>
            <span>Categoría</span>
            <input value={form.categoria} onChange={(event) => setForm({ ...form, categoria: event.target.value })} required maxLength={80} placeholder="Ej. Reparaciones, compras, servicios" />
          </label>
          <label>
            <span>Monto</span>
            <input type="number" min="0" step="0.01" value={form.monto} onChange={(event) => setForm({ ...form, monto: event.target.value })} required />
          </label>
        </div>
        <label>
          <span>Descripción</span>
          <textarea value={form.descripcion} onChange={(event) => setForm({ ...form, descripcion: event.target.value })} required maxLength={300} placeholder="Detalle breve del movimiento registrado" />
        </label>
        <div className="modal-actions-row">
          <button type="button" className="secondary" onClick={onClose}>Cancelar</button>
          <button type="submit" disabled={loading}>{loading ? 'Guardando...' : 'Guardar movimiento'}</button>
        </div>
      </form>
    </Modal>
  );
}
