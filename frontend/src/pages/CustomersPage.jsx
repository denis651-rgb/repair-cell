import { useEffect, useMemo, useState } from 'react';
import { Mail, MapPin, PencilLine, Phone, Search, StickyNote, UserPlus, Users } from 'lucide-react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import EmptyState from '../components/common/EmptyState';
import CustomerModal from '../components/modals/CustomerModal';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import '../styles/pages/clientes.css';

const PAGE_SIZE = 8;
const initialForm = { nombreCompleto: '', telefono: '', email: '', direccion: '', notas: '' };
const initialPage = { content: [], totalPages: 0, totalElements: 0, number: 0 };

export default function CustomersPage() {
  const [customersPage, setCustomersPage] = useState(initialPage);
  const [form, setForm] = useState(initialForm);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(false);
  const debouncedSearch = useDebouncedValue(search, 250);

  const loadClientes = async (pagina = page, busqueda = debouncedSearch) => {
    try {
      setError('');
      setCustomersPage((await api.get('/clientes/paginado', {
        pagina,
        tamano: PAGE_SIZE,
        busqueda,
      })) || initialPage);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadClientes(page, debouncedSearch);
  }, [page, debouncedSearch]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const clientes = customersPage.content || [];

  const clientsWithEmail = useMemo(
    () => clientes.filter((cliente) => Boolean(cliente.email)).length,
    [clientes],
  );

  const clientsWithAddress = useMemo(
    () => clientes.filter((cliente) => Boolean(cliente.direccion)).length,
    [clientes],
  );

  const openCreate = () => {
    setEditingId(null);
    setForm(initialForm);
    setModalOpen(true);
  };

  const openEdit = (cliente) => {
    setEditingId(cliente.id);
    setForm({
      nombreCompleto: cliente.nombreCompleto || '',
      telefono: cliente.telefono || '',
      email: cliente.email || '',
      direccion: cliente.direccion || '',
      notas: cliente.notas || '',
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingId(null);
    setForm(initialForm);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      if (editingId) {
        await api.put(`/clientes/${editingId}`, form);
      } else {
        await api.post('/clientes', form);
      }
      closeModal();
      await loadClientes(page, debouncedSearch);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-stack customers-page">
      <PageHeader title="Clientes" subtitle="Organiza tu base de clientes, mantén la información esencial a la vista y edita sin fricción.">
        <div className="customers-header-actions">
          <button className="customers-primary-button" onClick={openCreate}>
            <UserPlus size={18} />
            Nuevo cliente
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="customers-hero-card">
        <label className="customers-search">
          <Search size={18} />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar por nombre, teléfono, correo, dirección o notas..."
          />
        </label>
        <div className="customers-search-summary">
          <span className="customers-dot" />
          {customersPage.totalElements} resultados
        </div>
      </section>

      <section className="customers-kpi-grid">
        <article className="customers-kpi-card">
          <div className="customers-kpi-icon icon-soft"><Users size={20} /></div>
          <span className="customers-kpi-label">Total clientes</span>
          <strong className="customers-kpi-value">{customersPage.totalElements}</strong>
          <p>Base completa según el filtro actual</p>
        </article>

        <article className="customers-kpi-card">
          <div className="customers-kpi-icon icon-warning"><Mail size={20} /></div>
          <span className="customers-kpi-label">Con correo</span>
          <strong className="customers-kpi-value">{clientsWithEmail}</strong>
          <p>Visibles en esta página</p>
        </article>

        <article className="customers-kpi-card customers-kpi-card-accent">
          <div className="customers-kpi-icon icon-soft"><MapPin size={20} /></div>
          <span className="customers-kpi-label">Con dirección</span>
          <strong className="customers-kpi-value">{clientsWithAddress}</strong>
          <p>Datos completos en esta página</p>
        </article>
      </section>

      <section className="customers-table-card card">
        <div className="customers-table-header">
          <div>
            <h3>Clientes registrados</h3>
            <p>{debouncedSearch.trim() ? 'Resultados paginados según tu búsqueda actual' : 'Vista general paginada de la base de clientes'}</p>
          </div>
          <span className="chip">{customersPage.totalElements} registros</span>
        </div>

        {clientes.length === 0 ? (
          <EmptyState
            title="No hay clientes para mostrar"
            description={customersPage.totalElements === 0 ? 'Crea el primer cliente para comenzar a registrar reparaciones.' : 'Prueba con otro criterio de búsqueda o limpia el filtro actual.'}
            action={<button onClick={openCreate}>Crear cliente</button>}
          />
        ) : (
          <>
            <div className="customers-table-wrap">
              <div className="customers-table-head">
                <span>Cliente</span>
                <span>Contacto</span>
                <span>Ubicación</span>
                <span>Notas</span>
                <span>Editar</span>
              </div>

              <div className="customers-list">
                {clientes.map((cliente) => (
                  <article key={cliente.id} className="customers-row">
                    <div className="customers-cell customers-client-main">
                      <div className="customers-avatar">
                        {cliente.nombreCompleto
                          ?.split(' ')
                          .filter(Boolean)
                          .slice(0, 2)
                          .map((part) => part[0]?.toUpperCase())
                          .join('') || 'CL'}
                      </div>
                      <div>
                        <strong>{cliente.nombreCompleto}</strong>
                        <p>Cliente listo para nuevas órdenes y seguimiento</p>
                      </div>
                    </div>

                    <div className="customers-cell customers-meta-stack">
                      <div className="customers-meta-item">
                        <Phone size={15} />
                        <span>{cliente.telefono}</span>
                      </div>
                      <div className="customers-meta-item">
                        <Mail size={15} />
                        <span>{cliente.email || 'Sin correo registrado'}</span>
                      </div>
                    </div>

                    <div className="customers-cell customers-meta-stack">
                      <div className="customers-meta-item">
                        <MapPin size={15} />
                        <span>{cliente.direccion || 'Sin dirección registrada'}</span>
                      </div>
                    </div>

                    <div className="customers-cell customers-notes-cell">
                      <div className="customers-note-pill">
                        <StickyNote size={15} />
                        <span>{cliente.notas || 'Sin notas'}</span>
                      </div>
                    </div>

                    <div className="customers-cell customers-actions-cell">
                      <button
                        type="button"
                        className="customers-edit-button"
                        onClick={() => openEdit(cliente)}
                        aria-label={`Editar ${cliente.nombreCompleto}`}
                        title={`Editar ${cliente.nombreCompleto}`}
                      >
                        <PencilLine size={16} />
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <div className="pagination-row customers-pagination-row">
              <button
                className="secondary"
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
              >
                Anterior
              </button>

              <span>
                Página {customersPage.number + 1} de {Math.max(customersPage.totalPages || 1, 1)}
              </span>

              <button
                className="secondary"
                type="button"
                disabled={page + 1 >= (customersPage.totalPages || 1)}
                onClick={() => setPage((current) => current + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>

      <CustomerModal
        open={modalOpen}
        onClose={closeModal}
        onSubmit={handleSubmit}
        form={form}
        setForm={setForm}
        loading={loading}
        editing={Boolean(editingId)}
      />
    </div>
  );
}
