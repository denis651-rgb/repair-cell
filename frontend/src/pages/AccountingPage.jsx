import { useEffect, useMemo, useState } from 'react';
import {
  CalendarRange,
  CircleDollarSign,
  DollarSign,
  LockOpen,
  PlusCircle,
  ReceiptText,
  Search,
  ShieldCheck,
  Wallet,
} from 'lucide-react';
import { api } from '../api/api';
import EntryModal from '../components/modals/EntryModal';
import PageHeader from '../components/PageHeader';
import { formatDate, formatDateTime, money } from '../utils/formatters';
import '../styles/pages/contabilidad.css';

const PAGE_SIZE = 8;
const today = new Date().toISOString().slice(0, 10);
const initialForm = { tipoEntrada: 'ENTRADA', categoria: '', descripcion: '', monto: 0, fechaEntrada: today };
const initialPage = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const initialCashSummary = { entradas: 0, salidas: 0, movimientos: 0, esperado: 0 };

const moduloOptions = [
  { value: '', label: 'Todos los modulos' },
  { value: 'ORDEN_REPARACION', label: 'Ordenes' },
  { value: 'COMPRA', label: 'Compras' },
  { value: 'VENTA', label: 'Ventas' },
  { value: 'DEVOLUCION_VENTA', label: 'Devoluciones' },
  { value: 'ABONO_CUENTA_POR_COBRAR', label: 'Cuentas por cobrar' },
  { value: 'MANUAL', label: 'Manual' },
];

export default function AccountingPage() {
  const [entriesPage, setEntriesPage] = useState(initialPage);
  const [balance, setBalance] = useState(null);
  const [range, setRange] = useState({ fechaInicio: today, fechaFin: today });
  const [filters, setFilters] = useState({ busqueda: '', tipoEntrada: '', moduloRelacionado: '' });
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState('');
  const [entryModalOpen, setEntryModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [pageLoading, setPageLoading] = useState(true);
  const [entriesPageIndex, setEntriesPageIndex] = useState(0);
  const [caja, setCaja] = useState(null);
  const [cashSummary, setCashSummary] = useState(initialCashSummary);
  const [montoApertura, setMontoApertura] = useState('');
  const [montoCierre, setMontoCierre] = useState('');
  const [observacionesCierre, setObservacionesCierre] = useState('');

  const user = JSON.parse(localStorage.getItem('user') || '{}');

  const loadEntriesPage = async (pagina = entriesPageIndex, dates = range, activeFilters = filters) => {
    const data = await api.get('/contabilidad/entradas/paginado', {
      fechaInicio: dates.fechaInicio,
      fechaFin: dates.fechaFin,
      pagina,
      tamano: PAGE_SIZE,
      busqueda: activeFilters.busqueda,
      tipoEntrada: activeFilters.tipoEntrada || undefined,
      moduloRelacionado: activeFilters.moduloRelacionado || undefined,
    });
    setEntriesPage(data || initialPage);
    return data || initialPage;
  };

  const loadBalance = async (dates = range) => {
    const data = await api.get('/contabilidad/balance', dates);
    setBalance(data);
    return data;
  };

  const loadCaja = async () => {
    const data = await api.get('/contabilidad/caja/actual');
    setCaja(data);
    return data;
  };

  const loadCashSummary = async () => {
    const data = await api.get('/contabilidad/caja/resumen-actual');
    setCashSummary(data || initialCashSummary);
    return data || initialCashSummary;
  };

  const loadPage = async (pagina = entriesPageIndex, dates = range, activeFilters = filters) => {
    setPageLoading(true);
    setError('');
    try {
      await Promise.all([loadEntriesPage(pagina, dates, activeFilters), loadBalance(dates), loadCaja(), loadCashSummary()]);
    } catch (err) {
      setError(err.message);
    } finally {
      setPageLoading(false);
    }
  };

  useEffect(() => {
    loadPage(entriesPageIndex, range, filters);
  }, [entriesPageIndex]);

  useEffect(() => {
    setEntriesPageIndex(0);
    loadPage(0, range, filters);
  }, []);

  const entries = entriesPage.content || [];
  const entriesCount = entriesPage.totalElements || 0;
  const expectedCash = caja ? Number(cashSummary.esperado || 0) : 0;
  const automaticEntries = entries.filter((entry) => entry.moduloRelacionado).length;
  const manualEntries = entries.filter((entry) => !entry.moduloRelacionado).length;

  const entriesTotalVisible = useMemo(
    () => entries.filter((entry) => entry.tipoEntrada === 'ENTRADA').reduce((sum, entry) => sum + Number(entry.monto || 0), 0),
    [entries],
  );

  const abrirCaja = async () => {
    try {
      setError('');
      await api.post('/contabilidad/caja/abrir', {
        montoApertura: Number(montoApertura),
        usuario: user.nombre || 'Administrador',
      });
      setMontoApertura('');
      await loadPage(entriesPageIndex, range, filters);
    } catch (err) {
      setError(err.message);
    }
  };

  const cerrarCaja = async () => {
    try {
      setError('');
      await api.post('/contabilidad/caja/cerrar', {
        id: caja.id,
        montoCierre: Number(montoCierre),
        usuario: user.nombre || 'Administrador',
        observaciones: observacionesCierre,
      });
      setMontoCierre('');
      setObservacionesCierre('');
      await loadPage(entriesPageIndex, range, filters);
    } catch (err) {
      setError(err.message);
    }
  };

  const submitEntry = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      await api.post('/contabilidad/entradas', { ...form, monto: Number(form.monto) });
      setEntryModalOpen(false);
      setForm(initialForm);
      await loadPage(entriesPageIndex, range, filters);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const applyBalanceFilter = async () => {
    try {
      setError('');
      setEntriesPageIndex(0);
      await loadPage(0, range, filters);
    } catch (err) {
      setError(err.message);
    }
  };

  const chipModulo = (entry) => {
    if (!entry.moduloRelacionado) return 'chip';
    if (entry.moduloRelacionado === 'VENTA' || entry.moduloRelacionado === 'ORDEN_REPARACION') return 'chip accounting-chip-auto';
    if (entry.moduloRelacionado === 'COMPRA' || entry.moduloRelacionado === 'DEVOLUCION_VENTA') return 'badge badge-danger';
    return 'chip accounting-chip-neutral';
  };

  return (
    <div className="page-stack accounting-page">
      <PageHeader title="Contabilidad" subtitle="Caja diaria, movimientos automaticos por modulo y trazabilidad financiera del negocio.">
        <div className="accounting-header-actions">
          <button className="secondary accounting-ghost-button" type="button" onClick={() => loadPage(entriesPageIndex, range, filters)}>
            <CalendarRange size={16} />
            Actualizar
          </button>
          <button type="button" onClick={() => setEntryModalOpen(true)} disabled={!caja}>
            <PlusCircle size={16} />
            Movimiento
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="accounting-status-strip">
        <div className={`accounting-status-pill ${caja ? 'is-open' : 'is-closed'}`}>
          <ShieldCheck size={14} />
          {caja ? 'Caja abierta' : 'Caja cerrada'}
        </div>
        <p>
          {caja
            ? `Caja activa desde ${formatDateTime(caja.fechaApertura)}. Los registros automaticos y manuales se vinculan a esta jornada.`
            : 'Abre una caja diaria para habilitar el registro de ingresos y egresos.'}
        </p>
      </section>

      <section className="accounting-kpi-grid">
        <article className="accounting-kpi-card">
          <div className="accounting-kpi-icon icon-soft"><CircleDollarSign size={18} /></div>
          <span className="accounting-kpi-label">Entradas</span>
          <strong className="accounting-kpi-value">{money.format(balance?.entradas ?? 0)}</strong>
          <p>Ingresos del rango seleccionado</p>
        </article>

        <article className="accounting-kpi-card">
          <div className="accounting-kpi-icon icon-danger"><ReceiptText size={18} /></div>
          <span className="accounting-kpi-label">Salidas</span>
          <strong className="accounting-kpi-value">{money.format(balance?.salidas ?? 0)}</strong>
          <p>Egresos acumulados del periodo</p>
        </article>

        <article className="accounting-kpi-card">
          <div className="accounting-kpi-icon icon-warning"><DollarSign size={18} /></div>
          <span className="accounting-kpi-label">Automaticos visibles</span>
          <strong className="accounting-kpi-value">{automaticEntries}</strong>
          <p>{manualEntries} manuales en la pagina actual</p>
        </article>

        <article className="accounting-kpi-card accounting-kpi-card-accent">
          <div className="accounting-kpi-icon icon-soft"><Wallet size={18} /></div>
          <span className="accounting-kpi-label">Entradas visibles</span>
          <strong className="accounting-kpi-value">{money.format(entriesTotalVisible)}</strong>
          <p>Monto visible en esta pagina</p>
        </article>
      </section>

      <section className="accounting-layout">
        <div className="accounting-main-column">
          <article className="card accounting-panel accounting-cash-panel">
            <div className="accounting-panel-header">
              <div>
                <h3>Caja diaria</h3>
                <p>Apertura, control esperado y cierre operativo de la jornada.</p>
              </div>
              <span className={`chip ${caja ? '' : 'accounting-chip-muted'}`}>{caja ? 'Activa' : 'Pendiente'}</span>
            </div>

            {!caja ? (
              <div className="accounting-open-box">
                <div className="accounting-helper-card">
                  <strong>Antes de registrar movimientos</strong>
                  <p>Define el monto inicial de caja. Esto habilita ingresos, egresos y el calculo esperado del cierre.</p>
                </div>

                <div className="entity-form">
                  <label>
                    <span>Monto de apertura</span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={montoApertura}
                      onChange={(event) => setMontoApertura(event.target.value)}
                    />
                  </label>
                  <button type="button" onClick={abrirCaja} disabled={!montoApertura}>
                    <LockOpen size={16} />
                    Abrir caja
                  </button>
                </div>
              </div>
            ) : (
              <div className="accounting-cash-grid">
                <div className="accounting-summary-grid">
                  <div className="accounting-summary-card">
                    <span>Apertura</span>
                    <strong>{money.format(caja.montoApertura || 0)}</strong>
                    <p>{formatDateTime(caja.fechaApertura)}</p>
                  </div>
                  <div className="accounting-summary-card">
                    <span>Entradas en caja</span>
                    <strong>{money.format(cashSummary.entradas || 0)}</strong>
                    <p>{cashSummary.movimientos || 0} movimientos acumulados</p>
                  </div>
                  <div className="accounting-summary-card">
                    <span>Salidas en caja</span>
                    <strong>{money.format(cashSummary.salidas || 0)}</strong>
                    <p>Resumen de egresos de la caja activa</p>
                  </div>
                  <div className="accounting-summary-card accounting-summary-card-accent">
                    <span>Esperado al cierre</span>
                    <strong>{money.format(expectedCash)}</strong>
                    <p>Calculado con apertura y movimientos</p>
                  </div>
                </div>

                <div className="accounting-close-box">
                  <label>
                    <span>Monto de cierre</span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={montoCierre}
                      onChange={(event) => setMontoCierre(event.target.value)}
                    />
                  </label>
                  <label>
                    <span>Observaciones</span>
                    <textarea
                      value={observacionesCierre}
                      onChange={(event) => setObservacionesCierre(event.target.value)}
                      placeholder="Diferencias, retiros, pagos o notas del turno..."
                    />
                  </label>
                  <button
                    type="button"
                    className="secondary accounting-close-button"
                    onClick={cerrarCaja}
                    disabled={!montoCierre}
                  >
                    <DollarSign size={16} />
                    Cerrar caja
                  </button>
                </div>
              </div>
            )}
          </article>
        </div>

        <aside className="accounting-side-column">
          <article className="card accounting-panel accounting-filter-panel">
            <div className="accounting-panel-header">
              <div>
                <h3>Balance por rango</h3>
                <p>Consulta rapidamente como se comporta el flujo de caja por fechas.</p>
              </div>
            </div>

            <div className="form-grid two-columns">
              <label>
                <span>Fecha inicio</span>
                <input
                  type="date"
                  value={range.fechaInicio}
                  onChange={(event) => setRange({ ...range, fechaInicio: event.target.value })}
                />
              </label>
              <label>
                <span>Fecha fin</span>
                <input
                  type="date"
                  value={range.fechaFin}
                  onChange={(event) => setRange({ ...range, fechaFin: event.target.value })}
                />
              </label>
            </div>

            <button type="button" onClick={applyBalanceFilter}>
              <CalendarRange size={16} />
              Actualizar balance
            </button>

            <div className="accounting-range-summary">
              <div>
                <span>Periodo</span>
                <strong>{formatDate(range.fechaInicio)} - {formatDate(range.fechaFin)}</strong>
              </div>
              <div>
                <span>Resultado</span>
                <strong>{money.format(balance?.balance ?? 0)}</strong>
              </div>
            </div>
          </article>
        </aside>
      </section>

      <article className="card accounting-panel accounting-table-panel accounting-table-panel-full">
        <div className="accounting-panel-header">
          <div>
            <h3>Movimientos registrados</h3>
            <p>Lista paginada con filtros por texto, tipo y modulo relacionado.</p>
          </div>
          <span className="chip">{entriesCount} movimientos</span>
        </div>

        <div className="accounting-filters-grid">
          <label className="inventory-search accounting-search-inline">
            <Search size={14} />
            <input
              value={filters.busqueda}
              onChange={(event) => setFilters((current) => ({ ...current, busqueda: event.target.value }))}
              placeholder="Buscar categoria, descripcion o modulo"
            />
          </label>

          <select
            value={filters.tipoEntrada}
            onChange={(event) => setFilters((current) => ({ ...current, tipoEntrada: event.target.value }))}
          >
            <option value="">Todos los tipos</option>
            <option value="ENTRADA">Entradas</option>
            <option value="SALIDA">Salidas</option>
          </select>

          <select
            value={filters.moduloRelacionado}
            onChange={(event) => setFilters((current) => ({ ...current, moduloRelacionado: event.target.value }))}
          >
            {moduloOptions.map((option) => (
              <option key={option.value || 'all'} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          <button type="button" className="secondary" onClick={applyBalanceFilter}>
            Aplicar
          </button>
        </div>

        {pageLoading ? (
          <div className="empty-state">Cargando contabilidad...</div>
        ) : entries.length === 0 ? (
          <div className="empty-state">No hay movimientos en el rango seleccionado.</div>
        ) : (
          <>
            <div className="responsive-table-wrap accounting-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Fecha</th>
                    <th>Tipo</th>
                    <th>Categoria</th>
                    <th>Descripcion</th>
                    <th>Modulo</th>
                    <th>Monto</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry) => (
                    <tr key={entry.id}>
                      <td>{formatDate(entry.fechaEntrada)}</td>
                      <td>
                        <span className={`accounting-entry-pill ${entry.tipoEntrada === 'ENTRADA' ? 'is-entry' : 'is-exit'}`}>
                          {entry.tipoEntrada}
                        </span>
                      </td>
                      <td>{entry.categoria}</td>
                      <td>{entry.descripcion}</td>
                      <td>
                        <span className={chipModulo(entry)}>
                          {entry.moduloRelacionado || 'MANUAL'}
                        </span>
                      </td>
                      <td className="accounting-amount-cell">{money.format(entry.monto || 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="pagination-row accounting-pagination-row">
              <button
                className="secondary"
                type="button"
                disabled={entriesPageIndex <= 0}
                onClick={() => setEntriesPageIndex((current) => Math.max(current - 1, 0))}
              >
                Anterior
              </button>

              <span>
                Pagina {entriesPage.number + 1} de {Math.max(entriesPage.totalPages || 1, 1)}
              </span>

              <button
                className="secondary"
                type="button"
                disabled={entriesPageIndex + 1 >= (entriesPage.totalPages || 1)}
                onClick={() => setEntriesPageIndex((current) => current + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </article>

      <EntryModal
        open={entryModalOpen}
        onClose={() => setEntryModalOpen(false)}
        onSubmit={submitEntry}
        form={form}
        setForm={setForm}
        loading={loading}
      />
    </div>
  );
}
