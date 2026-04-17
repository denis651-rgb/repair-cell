import {
  Cable,
  Fingerprint,
  LockKeyhole,
  MapPinned,
  Palette,
  Search,
  Smartphone,
  UserRound,
  X,
} from 'lucide-react';
import Modal from '../common/Modal';

export default function DeviceModal({
  open,
  onClose,
  onSubmit,
  form,
  setForm,
  clientes,
  loading,
  editing,
  clientQuery,
  setClientQuery,
  selectedClientLabel,
  onSelectClient,
  onClearClient,
}) {
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
            <div className="device-modal-search-box">
              <Search size={14} />
              <input
                value={clientQuery}
                onChange={(event) => setClientQuery(event.target.value)}
                placeholder="Buscar por nombre, telefono o correo"
                required={!form.clienteId}
              />
              {clientQuery && (
                <button
                  type="button"
                  className="device-modal-search-clear"
                  onClick={onClearClient}
                  aria-label="Limpiar cliente"
                >
                  <X size={14} />
                </button>
              )}
            </div>

            {clientQuery.trim() && (
              <div className="device-modal-search-results">
                {clientes.length === 0 ? (
                  <div className="device-modal-search-empty">No se encontraron clientes</div>
                ) : (
                  clientes.map((cliente) => (
                    <button
                      type="button"
                      key={cliente.id}
                      className={`device-modal-result-card ${String(form.clienteId) === String(cliente.id) ? 'active' : ''}`}
                      onClick={() => onSelectClient(cliente)}
                    >
                      <div className="device-modal-result-avatar">
                        {cliente.nombreCompleto
                          ?.split(' ')
                          .filter(Boolean)
                          .slice(0, 2)
                          .map((part) => part[0]?.toUpperCase())
                          .join('') || 'CL'}
                      </div>
                      <div className="device-modal-result-copy">
                        <strong>{cliente.nombreCompleto}</strong>
                        <span>{cliente.telefono || cliente.email || 'Sin contacto'}</span>
                      </div>
                    </button>
                  ))
                )}
              </div>
            )}

            {selectedClientLabel && !clientQuery.trim() && (
              <div className="device-modal-selected-card">
                <div className="device-modal-result-avatar">
                  {selectedClientLabel
                    .split(' ')
                    .filter(Boolean)
                    .slice(0, 2)
                    .map((part) => part[0]?.toUpperCase())
                    .join('') || 'CL'}
                </div>
                <div className="device-modal-result-copy">
                  <strong>{selectedClientLabel}</strong>
                  <span>Cliente seleccionado</span>
                </div>
                <button
                  type="button"
                  className="device-modal-selected-remove"
                  onClick={onClearClient}
                  aria-label="Quitar cliente"
                >
                  <X size={14} />
                </button>
              </div>
            )}
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
