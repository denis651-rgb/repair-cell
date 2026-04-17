import { Mail, MapPin, Phone, StickyNote, UserRound } from 'lucide-react';
import Modal from '../common/Modal';

export default function CustomerModal({ open, onClose, onSubmit, form, setForm, loading, editing }) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? 'Editar cliente' : 'Nuevo cliente'}
      subtitle="Completa la información principal del cliente para agilizar futuras órdenes y seguimientos."
      size="lg"
    >
      <form className="entity-form customer-modal-form" onSubmit={onSubmit}>
        <div className="customer-modal-intro">
          <div className="customer-modal-avatar">
            <UserRound size={22} />
          </div>
          <div>
            <strong>{editing ? 'Actualiza los datos del cliente' : 'Crea un nuevo perfil de cliente'}</strong>
            <p>{editing ? 'Puedes corregir contacto, dirección o notas sin salir de la página.' : 'Guarda nombre, teléfono, correo y observaciones desde un solo lugar.'}</p>
          </div>
        </div>

        <div className="form-grid two-columns">
          <label className="customer-modal-field">
            <span><UserRound size={15} /> Nombre completo</span>
            <input value={form.nombreCompleto} onChange={(event) => setForm({ ...form, nombreCompleto: event.target.value })} required maxLength={120} placeholder="Ej. Denis Choque" />
          </label>
          <label className="customer-modal-field">
            <span><Phone size={15} /> Teléfono</span>
            <input value={form.telefono} onChange={(event) => setForm({ ...form, telefono: event.target.value })} required maxLength={30} placeholder="Número principal de contacto" />
          </label>
          <label className="customer-modal-field">
            <span><Mail size={15} /> Correo</span>
            <input type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} maxLength={120} placeholder="cliente@correo.com" />
          </label>
          <label className="customer-modal-field">
            <span><MapPin size={15} /> Dirección</span>
            <input value={form.direccion} onChange={(event) => setForm({ ...form, direccion: event.target.value })} maxLength={180} placeholder="Zona, calle o referencia útil" />
          </label>
        </div>

        <label className="customer-modal-field">
          <span><StickyNote size={15} /> Notas</span>
          <textarea value={form.notas} onChange={(event) => setForm({ ...form, notas: event.target.value })} maxLength={500} placeholder="Preferencias, referencias o detalles importantes del cliente" />
        </label>

        <div className="modal-actions-row customer-modal-actions">
          <button type="button" className="secondary" onClick={onClose}>Cancelar</button>
          <button type="submit" disabled={loading}>{loading ? 'Guardando...' : editing ? 'Guardar cambios' : 'Crear cliente'}</button>
        </div>
      </form>
    </Modal>
  );
}
