import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ClipboardList,
  Eye,
  FileText,
  History,
  PlusCircle,
  Search,
  Trash2,
  Wrench,
  Pencil,
} from 'lucide-react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import Modal from '../components/common/Modal';
import EmptyState from '../components/common/EmptyState';
import RepairOrderModal from '../components/modals/RepairOrderModal';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { formatDateTime, money } from '../utils/formatters';
import '../styles/pages/ordenes.css';

const DRAFTS_STORAGE_KEY = 'repair-order-drafts';

const initialForm = {
  clienteId: '',
  dispositivoId: '',
  problemaReportado: '',
  diagnosticoTecnico: '',
  tecnicoResponsable: '',
  costoEstimado: 0,
  costoFinal: 0,
  fechaEntregaEstimada: '',
  diasGarantia: 0,
  nombreFirmaCliente: '',
  textoConfirmacion: '',
  partes: [],
};

const initialPart = { productoId: '', nombreParte: '', cantidad: 1, tipoFuente: 'TIENDA' };
const estados = ['RECIBIDO', 'EN_DIAGNOSTICO', 'EN_REPARACION', 'LISTO', 'ENTREGADO', 'CANCELADO'];

function getSessionUser() {
  try {
    return (
      JSON.parse(localStorage.getItem('usuarioActual')) ||
      JSON.parse(localStorage.getItem('user')) ||
      {}
    );
  } catch {
    return {};
  }
}

function getDrafts() {
  try {
    return JSON.parse(localStorage.getItem(DRAFTS_STORAGE_KEY) || '[]');
  } catch {
    return [];
  }
}

function saveDrafts(drafts) {
  localStorage.setItem(DRAFTS_STORAGE_KEY, JSON.stringify(drafts));
}

function getStatusTone(status) {
  switch (status) {
    case 'ENTREGADO':
      return 'is-success';
    case 'CANCELADO':
      return 'is-danger';
    case 'LISTO':
      return 'is-info';
    case 'EN_REPARACION':
      return 'is-warning';
    default:
      return 'is-neutral';
  }
}

export default function RepairOrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialBusqueda = searchParams.get('busqueda') || '';
  const currentPage = Number(searchParams.get('pagina') || 0);

  const [ordersPage, setOrdersPage] = useState({ content: [], totalPages: 0, number: 0 });
  const [clientes, setClientes] = useState([]);
  const [dispositivos, setDispositivos] = useState([]);
  const [products, setProducts] = useState([]);

  const [form, setForm] = useState(() => {
    const user = getSessionUser();
    return {
      ...initialForm,
      tecnicoResponsable:
        user?.nombreCompleto ||
        user?.nombre ||
        user?.username ||
        user?.email ||
        '',
    };
  });

  const [selectedPart, setSelectedPart] = useState(initialPart);
  const [searchOrders, setSearchOrders] = useState(initialBusqueda);
  const [clientQuery, setClientQuery] = useState('');
  const [deviceQuery, setDeviceQuery] = useState('');
  const [error, setError] = useState('');
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [historyItems, setHistoryItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [drafts, setDrafts] = useState([]);
  const [draftsModalOpen, setDraftsModalOpen] = useState(false);
  const [editingDraftId, setEditingDraftId] = useState(null);

  const debouncedOrderSearch = useDebouncedValue(searchOrders, 400);
  const debouncedClientQuery = useDebouncedValue(clientQuery, 250);
  const debouncedDeviceQuery = useDebouncedValue(deviceQuery, 250);

  const loadCatalogs = async () => {
    const [clientesData, dispositivosData, productosData] = await Promise.all([
      api.get('/clientes'),
      api.get('/dispositivos'),
      api.get('/inventario/productos'),
    ]);
    setClientes(clientesData || []);
    setDispositivos(dispositivosData || []);
    setProducts(productosData || []);
  };

  const loadOrders = async (pagina = currentPage, busqueda = debouncedOrderSearch) => {
    const data = await api.get('/ordenes-reparacion/paginado', {
      pagina,
      tamano: 8,
      busqueda,
    });
    setOrdersPage(data);
  };

  useEffect(() => {
    setDrafts(getDrafts());
  }, []);

  useEffect(() => {
    Promise.all([loadCatalogs(), loadOrders(currentPage, debouncedOrderSearch)]).catch((err) => {
      setError(err.message);
    });
  }, []);

  useEffect(() => {
    const params = {};
    if (debouncedOrderSearch.trim()) params.busqueda = debouncedOrderSearch.trim();
    params.pagina = '0';
    setSearchParams(params, { replace: true });

    loadOrders(0, debouncedOrderSearch).catch((err) => setError(err.message));
  }, [debouncedOrderSearch]);

  const filteredClientes = useMemo(() => {
    const term = debouncedClientQuery.trim().toLowerCase();
    if (!term) return [];

    return clientes.filter((cliente) =>
      [cliente.nombreCompleto, cliente.telefono, cliente.email]
        .some((value) => String(value || '').toLowerCase().includes(term))
    );
  }, [clientes, debouncedClientQuery]);

  const dispositivosPorCliente = useMemo(() => {
    const base = dispositivos.filter(
      (device) => String(device.cliente?.id) === String(form.clienteId)
    );

    const term = debouncedDeviceQuery.trim().toLowerCase();
    if (!term) return [];

    return base.filter((device) =>
      [device.marca, device.modelo, device.imeiSerie]
        .some((value) => String(value || '').toLowerCase().includes(term))
    );
  }, [dispositivos, form.clienteId, debouncedDeviceQuery]);

  const selectedClient = useMemo(
    () => clientes.find((cliente) => String(cliente.id) === String(form.clienteId)),
    [clientes, form.clienteId]
  );

  const selectedDevice = useMemo(
    () => dispositivos.find((device) => String(device.id) === String(form.dispositivoId)),
    [dispositivos, form.dispositivoId]
  );

  const deliveredCount = useMemo(
    () => ordersPage.content.filter((order) => order.estado === 'ENTREGADO').length,
    [ordersPage.content]
  );

  const pendingCount = useMemo(
    () => ordersPage.content.filter((order) => order.estado !== 'ENTREGADO' && order.estado !== 'CANCELADO').length,
    [ordersPage.content]
  );

  const totalVisibleValue = useMemo(
    () => ordersPage.content.reduce((sum, order) => sum + Number(order.costoFinal || order.costoEstimado || 0), 0),
    [ordersPage.content]
  );

  useEffect(() => {
    if (!selectedClient) return;

    setForm((current) => ({
      ...current,
      nombreFirmaCliente: selectedClient.nombreCompleto || '',
      textoConfirmacion: selectedClient.nombreCompleto || '',
    }));
  }, [selectedClient?.id]);

  const addPart = () => {
    if (!selectedPart.productoId && !selectedPart.nombreParte.trim()) return;

    setForm((current) => ({
      ...current,
      partes: [
        ...current.partes,
        {
          ...selectedPart,
          cantidad: Number(selectedPart.cantidad || 1),
          productoId: selectedPart.productoId || null,
        },
      ],
    }));

    setSelectedPart(initialPart);
  };

  const removePart = (index) => {
    setForm((current) => ({
      ...current,
      partes: current.partes.filter((_, partIndex) => partIndex !== index),
    }));
  };

  const resetFormWithSessionUser = () => {
    const user = getSessionUser();

    setForm({
      ...initialForm,
      tecnicoResponsable:
        user?.nombreCompleto ||
        user?.nombre ||
        user?.username ||
        user?.email ||
        '',
    });

    setSelectedPart(initialPart);
    setClientQuery('');
    setDeviceQuery('');
    setEditingDraftId(null);
  };

  const closeCreateModal = () => {
    setCreateModalOpen(false);
    resetFormWithSessionUser();
  };

  const handleSelectClient = (cliente) => {
    setForm((current) => ({
      ...current,
      clienteId: String(cliente.id),
      dispositivoId: '',
      nombreFirmaCliente: cliente.nombreCompleto || '',
      textoConfirmacion: cliente.nombreCompleto || '',
    }));

    setClientQuery('');
    setDeviceQuery('');
  };

  const handleSelectDevice = (dispositivo) => {
    setForm((current) => ({
      ...current,
      dispositivoId: String(dispositivo.id),
    }));

    setDeviceQuery('');
  };

  const handleClearClient = () => {
    setClientQuery('');
    setDeviceQuery('');
    setForm((current) => ({
      ...current,
      clienteId: '',
      dispositivoId: '',
      nombreFirmaCliente: '',
      textoConfirmacion: '',
    }));
  };

  const handleClearDevice = () => {
    setDeviceQuery('');
    setForm((current) => ({
      ...current,
      dispositivoId: '',
    }));
  };

  const persistDrafts = (nextDrafts) => {
    setDrafts(nextDrafts);
    saveDrafts(nextDrafts);
  };

  const handleSaveDraft = () => {
    setError('');

    if (!form.clienteId && !form.problemaReportado.trim()) {
      setError('Para guardar borrador, selecciona un cliente o registra el problema reportado.');
      return;
    }

    const draft = {
      id: editingDraftId || crypto.randomUUID(),
      savedAt: new Date().toISOString(),
      estadoLocal: 'BORRADOR',
      form: {
        ...form,
        costoEstimado: Number(form.costoEstimado || 0),
        costoFinal: Number(form.costoFinal || 0),
        diasGarantia: Number(form.diasGarantia || 0),
        partes: form.partes.map((part) => ({
          ...part,
          cantidad: Number(part.cantidad || 1),
          productoId: part.productoId ? String(part.productoId) : '',
        })),
      },
    };

    const currentDrafts = getDrafts();
    const exists = currentDrafts.some((item) => item.id === draft.id);

    const nextDrafts = exists
      ? currentDrafts.map((item) => (item.id === draft.id ? draft : item))
      : [draft, ...currentDrafts];

    persistDrafts(nextDrafts);
    closeCreateModal();
  };

  const handleEditDraft = (draft) => {
    setEditingDraftId(draft.id);
    setForm({
      ...initialForm,
      ...draft.form,
    });

    setClientQuery('');
    setDeviceQuery('');
    setDraftsModalOpen(false);
    setCreateModalOpen(true);
  };

  const handleDeleteDraft = (draftId) => {
    const nextDrafts = drafts.filter((item) => item.id !== draftId);
    persistDrafts(nextDrafts);
  };

  const removeCurrentDraftIfNeeded = () => {
    if (!editingDraftId) return;

    const nextDrafts = drafts.filter((item) => item.id !== editingDraftId);
    persistDrafts(nextDrafts);
    setEditingDraftId(null);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      await api.post('/ordenes-reparacion', {
        ...form,
        clienteId: Number(form.clienteId),
        dispositivoId: Number(form.dispositivoId),
        costoEstimado: Number(form.costoEstimado || 0),
        costoFinal: Number(form.costoFinal || 0),
        diasGarantia: Number(form.diasGarantia || 0),
        partes: form.partes.map((part) => ({
          ...part,
          productoId: part.productoId ? Number(part.productoId) : null,
        })),
      });

      removeCurrentDraftIfNeeded();
      closeCreateModal();
      await loadOrders(0, debouncedOrderSearch);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const updateStatus = async (id, estado) => {
    try {
      await api.patch(`/ordenes-reparacion/${id}/estado`, { estado });
      await loadOrders(currentPage, debouncedOrderSearch);
    } catch (err) {
      setError(err.message);
    }
  };

  const openHistory = async (id) => {
    try {
      setHistoryItems(await api.get(`/ordenes-reparacion/${id}/historial`));
      setHistoryModalOpen(true);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="page-stack repair-orders-page">
      <PageHeader
        title="Órdenes de reparación"
        subtitle="Gestiona ingresos, estados, borradores e historial desde una vista más clara y lista para el trabajo diario."
      >
        <div className="header-actions-wrap">
          <button className="secondary repair-orders-ghost-button" onClick={() => setDraftsModalOpen(true)}>
            <FileText size={18} />
            Borradores ({drafts.length})
          </button>

          <button
            className="repair-orders-primary-button"
            onClick={() => {
              resetFormWithSessionUser();
              setCreateModalOpen(true);
            }}
          >
            <PlusCircle size={18} />
            Nueva orden
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="repair-orders-hero-card">
        <label className="repair-orders-search">
          <Search size={18} />
          <input
            value={searchOrders}
            onChange={(e) => setSearchOrders(e.target.value)}
            placeholder="Buscar por número de orden o cliente"
          />
        </label>
        <div className="repair-orders-search-summary">
          <span className="repair-orders-dot" />
          {ordersPage.content.length} órdenes visibles
        </div>
      </section>

      <section className="repair-orders-kpi-grid">
        <article className="repair-orders-kpi-card">
          <div className="repair-orders-kpi-icon icon-soft"><ClipboardList size={20} /></div>
          <span className="repair-orders-kpi-label">Órdenes visibles</span>
          <strong className="repair-orders-kpi-value">{ordersPage.content.length}</strong>
          <p>Página actual del listado</p>
        </article>

        <article className="repair-orders-kpi-card">
          <div className="repair-orders-kpi-icon icon-warning"><Wrench size={20} /></div>
          <span className="repair-orders-kpi-label">Pendientes</span>
          <strong className="repair-orders-kpi-value">{pendingCount}</strong>
          <p>Órdenes aún en proceso</p>
        </article>

        <article className="repair-orders-kpi-card repair-orders-kpi-card-accent">
          <div className="repair-orders-kpi-icon icon-soft"><Eye size={20} /></div>
          <span className="repair-orders-kpi-label">Entregadas</span>
          <strong className="repair-orders-kpi-value">{deliveredCount}</strong>
          <p>Equipos cerrados en esta página</p>
        </article>

        <article className="repair-orders-kpi-card">
          <div className="repair-orders-kpi-icon icon-danger"><FileText size={20} /></div>
          <span className="repair-orders-kpi-label">Valor visible</span>
          <strong className="repair-orders-kpi-value repair-orders-kpi-value-money">{money.format(totalVisibleValue)}</strong>
          <p>Estimado entre costo final y estimado</p>
        </article>
      </section>

      <section className="repair-orders-list-card card">
        <div className="repair-orders-list-header">
          <div>
            <h3>Órdenes registradas</h3>
            <p>{debouncedOrderSearch.trim() ? 'Resultados filtrados por búsqueda actual' : 'Vista general de órdenes recientes y activas'}</p>
          </div>
          <span className="chip">{ordersPage.content.length} órdenes</span>
        </div>

        {ordersPage.content.length === 0 ? (
          <EmptyState
            title="No hay órdenes registradas"
            description="Crea una nueva orden desde el botón superior."
            action={<button onClick={() => setCreateModalOpen(true)}>Crear orden</button>}
          />
        ) : (
          <div className="repair-orders-list">
            {ordersPage.content.map((order) => (
              <article key={order.id} className="repair-order-card-row">
                <div className="repair-order-card-top">
                  <div>
                    <span className="repair-order-number">{order.numeroOrden}</span>
                    <h4>{order.cliente?.nombreCompleto || 'Sin cliente'}</h4>
                    <p>{formatDateTime(order.createdAt)}</p>
                  </div>
                  <span className={`repair-status-pill ${getStatusTone(order.estado)}`}>{order.estado}</span>
                </div>

                <div className="repair-order-card-grid">
                  <div className="repair-order-info-block">
                    <span>Dispositivo</span>
                    <strong>{order.dispositivo?.marca} {order.dispositivo?.modelo}</strong>
                    <p>{order.problemaReportado || 'Sin problema reportado'}</p>
                  </div>

                  <div className="repair-order-info-block">
                    <span>Técnico</span>
                    <strong>{order.tecnicoResponsable || 'Sin asignar'}</strong>
                    <p>{money.format(order.costoFinal || order.costoEstimado || 0)}</p>
                  </div>

                  <div className="repair-order-info-block">
                    <span>Estado</span>
                    <select value={order.estado} onChange={(e) => updateStatus(order.id, e.target.value)}>
                      {estados.map((estado) => (
                        <option key={estado} value={estado}>
                          {estado}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="repair-order-actions">
                    <Link className="secondary button-inline" to={`/tickets/${order.id}`}>
                      <Eye size={16} />
                      Ticket
                    </Link>

                    <button
                      type="button"
                      className="secondary button-inline"
                      onClick={() => openHistory(order.id)}
                    >
                      <History size={16} />
                      Historial
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <div className="pagination-row repair-pagination-row">
        <button
          className="secondary"
          type="button"
          disabled={currentPage <= 0}
          onClick={() => {
            const params = {};
            if (debouncedOrderSearch.trim()) params.busqueda = debouncedOrderSearch.trim();
            params.pagina = String(currentPage - 1);
            setSearchParams(params);
            loadOrders(currentPage - 1, debouncedOrderSearch);
          }}
        >
          Anterior
        </button>

        <span>
          Página {ordersPage.number + 1} de {Math.max(ordersPage.totalPages || 1, 1)}
        </span>

        <button
          className="secondary"
          type="button"
          disabled={currentPage + 1 >= (ordersPage.totalPages || 1)}
          onClick={() => {
            const params = {};
            if (debouncedOrderSearch.trim()) params.busqueda = debouncedOrderSearch.trim();
            params.pagina = String(currentPage + 1);
            setSearchParams(params);
            loadOrders(currentPage + 1, debouncedOrderSearch);
          }}
        >
          Siguiente
        </button>
      </div>

      <RepairOrderModal
        open={createModalOpen}
        onClose={closeCreateModal}
        onSubmit={handleSubmit}
        onSaveDraft={handleSaveDraft}
        form={form}
        setForm={setForm}
        clientes={filteredClientes}
        dispositivos={dispositivosPorCliente}
        products={products}
        selectedPart={selectedPart}
        setSelectedPart={setSelectedPart}
        addPart={addPart}
        removePart={removePart}
        loading={loading}
        clientQuery={clientQuery}
        setClientQuery={setClientQuery}
        deviceQuery={deviceQuery}
        setDeviceQuery={setDeviceQuery}
        selectedClientLabel={selectedClient?.nombreCompleto || ''}
        selectedDeviceLabel={
          selectedDevice
            ? `${selectedDevice.marca || ''} ${selectedDevice.modelo || ''}`.trim()
            : ''
        }
        onSelectClient={handleSelectClient}
        onSelectDevice={handleSelectDevice}
        onClearClient={handleClearClient}
        onClearDevice={handleClearDevice}
      />

      <Modal
        open={draftsModalOpen}
        onClose={() => setDraftsModalOpen(false)}
        title="Borradores"
        subtitle="Aquí puedes retomar órdenes incompletas."
      >
        {drafts.length === 0 ? (
          <EmptyState
            title="No hay borradores guardados"
            description="Guarda una orden como borrador para terminarla después."
          />
        ) : (
          <div className="drafts-list">
            {drafts.map((draft) => (
              <div className="draft-card" key={draft.id}>
                <div className="draft-card__content">
                  <strong>{draft.form.problemaReportado || 'Borrador sin problema reportado'}</strong>
                  <p>
                    Cliente ID: {draft.form.clienteId || 'Sin cliente'} · Dispositivo ID: {draft.form.dispositivoId || 'Sin dispositivo'}
                  </p>
                  <span>Guardado: {formatDateTime(draft.savedAt)}</span>
                </div>

                <div className="draft-card__actions">
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => handleEditDraft(draft)}
                  >
                    <Pencil size={16} />
                    Abrir
                  </button>

                  <button
                    type="button"
                    className="secondary danger-outline"
                    onClick={() => handleDeleteDraft(draft.id)}
                  >
                    <Trash2 size={16} />
                    Eliminar
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Modal
        open={historyModalOpen}
        onClose={() => setHistoryModalOpen(false)}
        title="Historial de la orden"
        subtitle="Trazabilidad de cambios de estado y eventos registrados."
      >
        {historyItems.length === 0 ? (
          <EmptyState
            title="Sin historial disponible"
            description="Esta orden todavía no tiene eventos registrados."
          />
        ) : (
          <div className="history-list">
            {historyItems.map((item) => (
              <div className="history-item" key={item.id}>
                <strong>{item.estadoNuevo || item.descripcion || 'Evento'}</strong>
                <p>{item.descripcion || 'Sin descripción'}</p>
                <span>{formatDateTime(item.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
}
