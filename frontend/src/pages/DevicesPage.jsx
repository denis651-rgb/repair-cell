import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import EmptyState from '../components/common/EmptyState';
import DeviceModal from '../components/modals/DeviceModal';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import '../styles/pages/dispositivos.css';

const PAGE_SIZE = 8;
const initialPage = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const initialForm = {
  clienteId: '',
  marca: '',
  modelo: '',
  imeiSerie: '',
  color: '',
  codigoBloqueo: '',
  accesorios: '',
  observaciones: '',
};

export default function DevicesPage() {
  const [devicesPage, setDevicesPage] = useState(initialPage);
  const [clientes, setClientes] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [clientQuery, setClientQuery] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(false);
  const debouncedSearch = useDebouncedValue(search, 250);
  const debouncedClientQuery = useDebouncedValue(clientQuery, 250);

  const loadCatalogs = async () => {
    try {
      setClientes((await api.get('/clientes')) || []);
    } catch (err) {
      setError(err.message);
    }
  };

  const loadDevices = async (pagina = page, busqueda = debouncedSearch) => {
    try {
      setError('');
      setDevicesPage((await api.get('/dispositivos/paginado', {
        pagina,
        tamano: PAGE_SIZE,
        busqueda,
      })) || initialPage);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadCatalogs();
  }, []);

  useEffect(() => {
    loadDevices(page, debouncedSearch);
  }, [page, debouncedSearch]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const dispositivos = devicesPage.content || [];

  const filteredClientes = useMemo(() => {
    const term = debouncedClientQuery.trim().toLowerCase();
    if (!term) return [];

    return clientes.filter((cliente) =>
      [cliente.nombreCompleto, cliente.telefono, cliente.email]
        .some((value) => String(value || '').toLowerCase().includes(term))
    );
  }, [clientes, debouncedClientQuery]);

  const selectedClient = useMemo(
    () => clientes.find((cliente) => String(cliente.id) === String(form.clienteId)),
    [clientes, form.clienteId],
  );

  const openCreate = () => {
    setEditingId(null);
    setForm(initialForm);
    setClientQuery('');
    setModalOpen(true);
  };

  const openEdit = (device) => {
    setEditingId(device.id);
    setForm({
      clienteId: String(device.cliente?.id || ''),
      marca: device.marca || '',
      modelo: device.modelo || '',
      imeiSerie: device.imeiSerie || '',
      color: device.color || '',
      codigoBloqueo: device.codigoBloqueo || '',
      accesorios: device.accesorios || '',
      observaciones: device.observaciones || '',
    });
    setClientQuery('');
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingId(null);
    setForm(initialForm);
    setClientQuery('');
  };

  const handleSelectClient = (cliente) => {
    setForm((current) => ({
      ...current,
      clienteId: String(cliente.id),
    }));
    setClientQuery('');
  };

  const handleClearClient = () => {
    setClientQuery('');
    setForm((current) => ({
      ...current,
      clienteId: '',
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    const payload = {
      marca: form.marca,
      modelo: form.modelo,
      imeiSerie: form.imeiSerie,
      color: form.color,
      codigoBloqueo: form.codigoBloqueo,
      accesorios: form.accesorios,
      observaciones: form.observaciones,
    };

    try {
      if (editingId) {
        await api.put(`/dispositivos/${editingId}`, payload, { clienteId: Number(form.clienteId) });
      } else {
        await api.post('/dispositivos', payload, { clienteId: Number(form.clienteId) });
      }
      closeModal();
      await loadDevices(page, debouncedSearch);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-stack devices-page">
      <PageHeader title="Dispositivos" subtitle="Organiza los equipos recibidos, su identificacion y los datos tecnicos de acceso en una sola vista.">
        <div className="devices-header-actions">
          <button className="devices-primary-button" onClick={openCreate}>
            Nuevo dispositivo
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="devices-hero-card">
        <label className="devices-search">
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar cliente, marca, modelo, IMEI, color o PIN..."
          />
        </label>
        <div className="devices-search-summary">
          {devicesPage.totalElements} resultados
        </div>
      </section>
      <section className="devices-table-card card">
        <div className="devices-table-header" />

        {dispositivos.length === 0 ? (
          <EmptyState
            title="No hay equipos registrados"
            description={devicesPage.totalElements === 0 ? 'Agrega el primer dispositivo para empezar a gestionar reparaciones.' : 'Prueba con otro criterio de busqueda o limpia el filtro actual.'}
            action={<button onClick={openCreate}>Nuevo dispositivo</button>}
          />
        ) : (
          <>
            <div className="devices-table-wrap">
              <div className="devices-table-head">
                <span>Cliente</span>
                <span>Equipo</span>
                <span>Identificador</span>
                <span>Color / acceso</span>
                <span>Editar</span>
              </div>

              <div className="devices-list">
                {dispositivos.map((device) => (
                  <article key={device.id} className="devices-row">
                    <div className="devices-cell devices-customer-main">
                      <div>
                        <strong>{device.cliente?.nombreCompleto || 'Sin cliente'}</strong>
                        <p>Equipo vinculado para seguimiento y reparacion</p>
                      </div>
                    </div>

                    <div className="devices-cell devices-equipment-main">
                      <div className="devices-meta-item">
                        <span>{device.marca} {device.modelo}</span>
                      </div>
                      <div className="devices-meta-item">
                        <span>{device.accesorios || 'Sin accesorios registrados'}</span>
                      </div>
                    </div>

                    <div className="devices-cell devices-meta-stack">
                      <div className="devices-meta-item">
                        <span>{device.imeiSerie || 'Sin IMEI / serie'}</span>
                      </div>
                      <div className="devices-meta-item">
                        <span>{device.observaciones || 'Sin observaciones'}</span>
                      </div>
                    </div>

                    <div className="devices-cell devices-meta-stack">
                      <div className="devices-chip-line">
                        <span className="devices-soft-chip">{device.color || 'Sin color'}</span>
                      </div>
                      <div className="devices-chip-line">
                        <span className="devices-soft-chip">{device.codigoBloqueo || 'Sin codigo'}</span>
                      </div>
                    </div>

                    <div className="devices-cell devices-actions-cell">
                      <button
                        type="button"
                        className="devices-edit-button"
                        onClick={() => openEdit(device)}
                        aria-label={`Editar ${device.marca} ${device.modelo}`}
                        title={`Editar ${device.marca} ${device.modelo}`}
                      >
                        Editar
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <div className="pagination-row devices-pagination-row">
              <button
                className="secondary"
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
              >
                Anterior
              </button>

              <span>
                Pagina {devicesPage.number + 1} de {Math.max(devicesPage.totalPages || 1, 1)}
              </span>

              <button
                className="secondary"
                type="button"
                disabled={page + 1 >= (devicesPage.totalPages || 1)}
                onClick={() => setPage((current) => current + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>

      <DeviceModal
        open={modalOpen}
        onClose={closeModal}
        onSubmit={handleSubmit}
        form={form}
        setForm={setForm}
        clientes={filteredClientes}
        loading={loading}
        editing={Boolean(editingId)}
        clientQuery={clientQuery}
        setClientQuery={setClientQuery}
        selectedClientLabel={selectedClient?.nombreCompleto || ''}
        onSelectClient={handleSelectClient}
        onClearClient={handleClearClient}
      />
    </div>
  );
}
