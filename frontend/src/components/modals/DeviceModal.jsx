import {
  Cable,
  Fingerprint,
  LockKeyhole,
  MapPinned,
  Palette,
  Smartphone,
  UserRound,
} from 'lucide-react';
import Modal from '../common/Modal';

export default function DeviceModal({ open, onClose, onSubmit, form, setForm, clientes, loading, editing }) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? 'Editar dispositivo' : 'Nuevo dispositivo'}
      subtitle="Relaciona el equipo con un cliente y documenta sus datos técnicos para el ingreso al taller."
      size="lg"
    >
      <form className="entity-form device-modal-form" onSubmit={onSubmit}>
        <div className="device-modal-intro">
          <div className="device-modal-avatar">
            <Smartphone size={22} />
          </div>
          <div>
            <strong>{editing ? 'Actualiza la ficha técnica del equipo' : 'Registra un nuevo dispositivo'}</strong>
            <p>{editing ? 'Puedes corregir cliente, identificación, accesorios o datos de acceso sin salir de la página.' : 'Guarda cliente, modelo, IMEI, color y observaciones desde un solo formulario.'}</p>
          </div>
        </div>

        <div className="form-grid two-columns">
          <label className="device-modal-field">
            <span><UserRound size={15} /> Cliente</span>
            <select value={form.clienteId} onChange={(event) => setForm({ ...form, clienteId: event.target.value })} required>
              <option value="">Selecciona un cliente</option>
              {clientes.map((cliente) => (
                <option key={cliente.id} value={cliente.id}>{cliente.nombreCompleto}</option>
              ))}
            </select>
          </label>
          <label className="device-modal-field">
            <span><Smartphone size={15} /> Marca</span>
            <input value={form.marca} onChange={(event) => setForm({ ...form, marca: event.target.value })} required maxLength={80} placeholder="Ej. Samsung, Xiaomi, iPhone" />
          </label>
          <label className="device-modal-field">
            <span><Smartphone size={15} /> Modelo</span>
            <input value={form.modelo} onChange={(event) => setForm({ ...form, modelo: event.target.value })} required maxLength={80} placeholder="Modelo comercial o referencia" />
          </label>
          <label className="device-modal-field">
            <span><Fingerprint size={15} /> IMEI / Serie</span>
            <input value={form.imeiSerie} onChange={(event) => setForm({ ...form, imeiSerie: event.target.value })} maxLength={80} placeholder="Identificador único del equipo" />
          </label>
          <label className="device-modal-field">
            <span><Palette size={15} /> Color</span>
            <input value={form.color} onChange={(event) => setForm({ ...form, color: event.target.value })} maxLength={40} placeholder="Color del dispositivo" />
          </label>
          <label className="device-modal-field">
            <span><LockKeyhole size={15} /> Patrón / PIN</span>
            <input value={form.codigoBloqueo} onChange={(event) => setForm({ ...form, codigoBloqueo: event.target.value })} maxLength={60} placeholder="Código de acceso si aplica" />
          </label>
        </div>

        <label className="device-modal-field">
          <span><Cable size={15} /> Accesorios</span>
          <textarea value={form.accesorios} onChange={(event) => setForm({ ...form, accesorios: event.target.value })} maxLength={250} placeholder="Cargador, funda, cable, chip u otros accesorios entregados" />
        </label>

        <label className="device-modal-field">
          <span><MapPinned size={15} /> Observaciones</span>
          <textarea value={form.observaciones} onChange={(event) => setForm({ ...form, observaciones: event.target.value })} maxLength={500} placeholder="Daños visibles, detalles del equipo o notas técnicas relevantes" />
        </label>

        <div className="modal-actions-row device-modal-actions">
          <button type="button" className="secondary" onClick={onClose}>Cancelar</button>
          <button type="submit" disabled={loading}>{loading ? 'Guardando...' : editing ? 'Guardar cambios' : 'Crear dispositivo'}</button>
        </div>
      </form>
    </Modal>
  );
}
