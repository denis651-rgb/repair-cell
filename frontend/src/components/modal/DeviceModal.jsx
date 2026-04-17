import { X } from 'lucide-react';

export default function DeviceModal({ show, onClose, onSubmit, form, setForm, clientes }) {

    if (!show) return null;

    return (
        <div className="modal-overlay">
            <div className="modal">

                <div className="modal-header">
                    <h3>Nuevo dispositivo</h3>
                    <button className="icon-btn" onClick={onClose}>
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="form-grid">

                    <select
                        value={form.clienteId}
                        onChange={(e) => setForm({ ...form, clienteId: e.target.value })}
                        required
                    >
                        <option value="">Selecciona cliente</option>
                        {clientes.map(c => (
                            <option key={c.id} value={c.id}>
                                {c.nombreCompleto}
                            </option>
                        ))}
                    </select>

                    <input placeholder="Marca" value={form.marca}
                        onChange={(e) => setForm({ ...form, marca: e.target.value })} />

                    <input placeholder="Modelo" value={form.modelo}
                        onChange={(e) => setForm({ ...form, modelo: e.target.value })} />

                    <input placeholder="IMEI / Serie" value={form.imei}
                        onChange={(e) => setForm({ ...form, imei: e.target.value })} />

                    <input placeholder="Color" value={form.color}
                        onChange={(e) => setForm({ ...form, color: e.target.value })} />

                    <input placeholder="Patrón / PIN" value={form.pin}
                        onChange={(e) => setForm({ ...form, pin: e.target.value })} />

                    <textarea placeholder="Accesorios"
                        value={form.accesorios}
                        onChange={(e) => setForm({ ...form, accesorios: e.target.value })}
                    />

                    <div className="modal-actions">
                        <button type="button" onClick={onClose}>Cancelar</button>
                        <button className="btn-primary" type="submit">Guardar</button>
                    </div>

                </form>
            </div>
        </div>
    );
}