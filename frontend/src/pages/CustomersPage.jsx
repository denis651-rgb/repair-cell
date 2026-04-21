import { useEffect, useState } from 'react';
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
      <PageHeader title="Clientes" subtitle="Organiza tu base de clientes, manten la informacion esencial a la vista y edita sin friccion.">
        <div className="customers-header-actions">
          <button className="customers-primary-button" onClick={openCreate}>
            Nuevo cliente
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="customers-hero-card">
        <label className="customers-search">
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar por nombre, telefono, correo, direccion o notas..."
          />
        </label>
        <div className="customers-search-summary">
          {customersPage.totalElements} resultados
        </div>
      </section>

      <section className="customers-table-card card">
        <div className="customers-table-header">
          <div>
            <h3>Clientes registrados</h3>
            <p>{debouncedSearch.trim() ? 'Resultados paginados segun tu busqueda actual' : 'Vista general paginada de la base de clientes'}</p>
          </div>
          <span className="chip">{customersPage.totalElements} registros</span>
        </div>

        {clientes.length === 0 ? (
          <EmptyState
            title="No hay clientes para mostrar"
            description={customersPage.totalElements === 0 ? 'Crea el primer cliente para comenzar a registrar reparaciones.' : 'Prueba con otro criterio de busqueda o limpia el filtro actual.'}
            action={<button onClick={openCreate}>Crear cliente</button>}
          />
        ) : (
          <>
            <div className="customers-table-wrap">
              <div className="customers-table-head">
                <span>Cliente</span>
                <span>Contacto</span>
                <span>Ubicacion</span>
                <span>Editar</span>
              </div>

              <div className="customers-list">
                {clientes.map((cliente) => (
                  <article key={cliente.id} className="customers-row">
                    <div className="customers-cell customers-client-main">
                      <div>
                        <strong>{cliente.nombreCompleto}</strong>
                        <p>Cliente listo para nuevas ordenes y seguimiento</p>
                      </div>
                    </div>

                    <div className="customers-cell customers-meta-stack">
                      <div className="customers-meta-item">
                        <span>{cliente.telefono}</span>
                      </div>
                      <div className="customers-meta-item">
                        <span>{cliente.email || 'Sin correo registrado'}</span>
                      </div>
                    </div>

                    <div className="customers-cell customers-meta-stack">
                      <div className="customers-meta-item">
                        <span>{cliente.direccion || 'Sin direccion registrada'}</span>
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
                        Editar
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
                Pagina {customersPage.number + 1} de {Math.max(customersPage.totalPages || 1, 1)}
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
