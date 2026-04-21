import { useEffect, useState } from 'react';
import { MapPin, Phone, Plus, Search } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import '../styles/pages/proveedores.css';

const TAMANO = 10;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const proveedorInicial = {
  nombreComercial: '',
  razonSocial: '',
  telefono: '',
  ciudad: '',
  direccion: '',
  nit: '',
  observaciones: '',
  activo: true,
};

export default function ProveedoresPage() {
  const [proveedoresPage, setProveedoresPage] = useState(paginaVacia);
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [proveedorForm, setProveedorForm] = useState(proveedorInicial);
  const [proveedorEditando, setProveedorEditando] = useState(null);
  const [error, setError] = useState('');

  const busquedaDebounced = useDebouncedValue(busqueda, 250);

  const cargarProveedores = async (paginaObjetivo = pagina) => {
    try {
      setError('');
      const respuesta = await api.get('/proveedores/paginado', {
        pagina: paginaObjetivo,
        tamano: TAMANO,
        busqueda: busquedaDebounced,
      });
      setProveedoresPage(respuesta || paginaVacia);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    setPagina(0);
  }, [busquedaDebounced]);

  useEffect(() => {
    cargarProveedores(pagina);
  }, [pagina, busquedaDebounced]);

  const abrirNuevo = () => {
    setProveedorEditando(null);
    setProveedorForm(proveedorInicial);
    setModalOpen(true);
  };

  const abrirEdicion = (proveedor) => {
    setProveedorEditando(proveedor);
    setProveedorForm({
      nombreComercial: proveedor.nombreComercial || '',
      razonSocial: proveedor.razonSocial || '',
      telefono: proveedor.telefono || '',
      ciudad: proveedor.ciudad || '',
      direccion: proveedor.direccion || '',
      nit: proveedor.nit || '',
      observaciones: proveedor.observaciones || '',
      activo: proveedor.activo ?? true,
    });
    setModalOpen(true);
  };

  const guardarProveedor = async (event) => {
    event.preventDefault();
    try {
      if (proveedorEditando) {
        await api.put(`/proveedores/${proveedorEditando.id}`, proveedorForm);
      } else {
        await api.post('/proveedores', proveedorForm);
      }
      setModalOpen(false);
      setProveedorForm(proveedorInicial);
      await cargarProveedores(0);
      setPagina(0);
    } catch (err) {
      setError(err.message);
    }
  };

  const proveedores = proveedoresPage.content || [];

  return (
    <div className="page-stack suppliers-page">
      <PageHeader title="Proveedores" subtitle="Base de proveedores para registrar compras y trazabilidad de abastecimiento.">
        <button className="inventory-primary-button compact" onClick={abrirNuevo}>
          <Plus size={16} />
          Proveedor
        </button>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="inventory-hero-card suppliers-toolbar-card">
        <div className="inventory-toolbar">
          <label className="inventory-search">
            <Search size={16} />
            <input
              value={busqueda}
              onChange={(event) => setBusqueda(event.target.value)}
              placeholder="Buscar proveedor por nombre, ciudad o telefono"
            />
          </label>
        </div>
      </section>

      <section className="card suppliers-table-card">
        <div className="inventory-panel-header">
          <div>
            <h3>Listado de proveedores</h3>
            <p>Consulta y administra las empresas o personas que abastecen el inventario.</p>
          </div>
          <span className="chip">{proveedoresPage.totalElements} registros</span>
        </div>

        {proveedores.length === 0 ? (
          <EmptyState title="No hay proveedores registrados" description="Registra el primer proveedor para empezar el flujo de compras." />
        ) : (
          <>
            <div className="responsive-table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Proveedor</th>
                    <th>Contacto</th>
                    <th>Ubicacion</th>
                    <th>NIT</th>
                    <th>Accion</th>
                  </tr>
                </thead>
                <tbody>
                  {proveedores.map((proveedor) => (
                    <tr key={proveedor.id}>
                      <td>
                        <strong>{proveedor.nombreComercial}</strong>
                        <div className="muted">{proveedor.razonSocial || 'Sin razon social'}</div>
                      </td>
                      <td>
                        <div className="suppliers-meta">
                          <Phone size={14} />
                          <span>{proveedor.telefono || 'Sin telefono'}</span>
                        </div>
                      </td>
                      <td>
                        <div className="suppliers-meta">
                          <MapPin size={14} />
                          <span>{proveedor.ciudad || 'Sin ciudad'}</span>
                        </div>
                      </td>
                      <td>{proveedor.nit || 'Sin NIT'}</td>
                      <td>
                        <button className="secondary compact" onClick={() => abrirEdicion(proveedor)}>
                          Editar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="pagination-row inventory-pagination-row">
              <button className="secondary" type="button" disabled={pagina <= 0} onClick={() => setPagina((actual) => Math.max(actual - 1, 0))}>
                Anterior
              </button>
              <span>
                Pagina {proveedoresPage.number + 1} de {Math.max(proveedoresPage.totalPages || 1, 1)}
              </span>
              <button className="secondary" type="button" disabled={pagina + 1 >= (proveedoresPage.totalPages || 1)} onClick={() => setPagina((actual) => actual + 1)}>
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={proveedorEditando ? 'Editar proveedor' : 'Nuevo proveedor'}
        subtitle="Registro base para compras y seguimiento de abastecimiento."
      >
        <form className="entity-form" onSubmit={guardarProveedor}>
          <div className="form-grid two-columns">
            <label>
              <span>Nombre comercial</span>
              <input value={proveedorForm.nombreComercial} onChange={(event) => setProveedorForm((actual) => ({ ...actual, nombreComercial: event.target.value }))} required />
            </label>
            <label>
              <span>Razon social</span>
              <input value={proveedorForm.razonSocial} onChange={(event) => setProveedorForm((actual) => ({ ...actual, razonSocial: event.target.value }))} />
            </label>
            <label>
              <span>Telefono</span>
              <input value={proveedorForm.telefono} onChange={(event) => setProveedorForm((actual) => ({ ...actual, telefono: event.target.value }))} />
            </label>
            <label>
              <span>Ciudad</span>
              <input value={proveedorForm.ciudad} onChange={(event) => setProveedorForm((actual) => ({ ...actual, ciudad: event.target.value }))} />
            </label>
            <label className="field-span-2">
              <span>Direccion</span>
              <input value={proveedorForm.direccion} onChange={(event) => setProveedorForm((actual) => ({ ...actual, direccion: event.target.value }))} />
            </label>
            <label>
              <span>NIT</span>
              <input value={proveedorForm.nit} onChange={(event) => setProveedorForm((actual) => ({ ...actual, nit: event.target.value }))} />
            </label>
          </div>
          <label>
            <span>Observaciones</span>
            <textarea value={proveedorForm.observaciones} onChange={(event) => setProveedorForm((actual) => ({ ...actual, observaciones: event.target.value }))} />
          </label>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalOpen(false)}>Cancelar</button>
            <button type="submit">Guardar proveedor</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
