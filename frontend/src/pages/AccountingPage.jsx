import { useEffect, useState } from 'react';
import {
  CalendarRange,
  CircleDollarSign,
  DollarSign,
  LockOpen,
  PlusCircle,
  ReceiptText,
  ShieldCheck,
  Wallet,
} from 'lucide-react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import EntryModal from '../components/modals/EntryModal';
import { formatDate, formatDateTime, money } from '../utils/formatters';
import '../styles/pages/contabilidad.css';

const PAGE_SIZE = 8;
const today = new Date().toISOString().slice(0, 10);
const initialForm = { tipoEntrada: 'ENTRADA', categoria: '', descripcion: '', monto: 0, fechaEntrada: today };
const initialPage = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const initialCashSummary = { entradas: 0, salidas: 0, movimientos: 0, esperado: 0 };

export default function AccountingPage() {
  const [entriesPage, setEntriesPage] = useState(initialPage);
  const [balance, setBalance] = useState(null);
  const [range, setRange] = useState({ fechaInicio: today, fechaFin: today });
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

  const loadEntriesPage = async (pagina = entriesPageIndex, dates = range) => {
    const data = await api.get('/contabilidad/entradas/paginado', {
      fechaInicio: dates.fechaInicio,
      fechaFin: dates.fechaFin,
      pagina,
      tamano: PAGE_SIZE,
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

  const loadPage = async (pagina = entriesPageIndex, dates = range) => {
    setPageLoading(true);
    setError('');
    try {
      await Promise.all([loadEntriesPage(pagina, dates), loadBalance(dates), loadCaja(), loadCashSummary()]);
    } catch (err) {
      setError(err.message);
    } finally {
      setPageLoading(false);
    }
  };

  useEffect(() => {
    loadPage(entriesPageIndex, range);
  }, [entriesPageIndex]);

  useEffect(() => {
    setEntriesPageIndex(0);
    loadPage(0, range);
  }, []);

  const entries = entriesPage.content || [];
  const entriesCount = entriesPage.totalElements || 0;
  const expectedCash = caja ? Number(cashSummary.esperado || 0) : 0;

  const abrirCaja = async () => {
    try {
      setError('');
      await api.post('/contabilidad/caja/abrir', {
        montoApertura: Number(montoApertura),
        usuario: user.nombre || 'Administrador',
      });
      setMontoApertura('');
      await loadPage(entriesPageIndex, range);
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
      await loadPage(entriesPageIndex, range);
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
      await loadPage(entriesPageIndex, range);
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
      await loadPage(0, range);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="page-stack accounting-page">
      <PageHeader title="Contabilidad" subtitle="Controla la caja diaria, registra movimientos y consulta el balance del periodo sin salir de esta vista.">
        <div className="accounting-header-actions">
          <button className="secondary accounting-ghost-button" type="button" onClick={() => loadPage(entriesPageIndex, range)}>
            <CalendarRange size={18} />
            Actualizar
          </button>
          <button type="button" onClick={() => setEntryModalOpen(true)} disabled={!caja}>
            <PlusCircle size={18} />
            Nuevo movimiento
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="accounting-status-strip">
        <div className={`accounting-status-pill ${caja ? 'is-open' : 'is-closed'}`}>
          <ShieldCheck size={16} />
          {caja ? 'Caja abierta' : 'Caja cerrada'}
        </div>
        <p>
          {caja
            ? `Caja activa desde ${formatDateTime(caja.fechaApertura)}. Puedes registrar movimientos y cerrar al final del turno.`
            : 'Abre una caja diaria para habilitar el registro de ingresos y egresos.'}
        </p>
      </section>

      <section className="accounting-kpi-grid">
        <article className="accounting-kpi-card">
          <div className="accounting-kpi-icon icon-soft"><CircleDollarSign size={20} /></div>
          <span className="accounting-kpi-label">Entradas</span>
          <strong className="accounting-kpi-value">{money.format(balance?.entradas ?? 0)}</strong>
          <p>Ingresos del rango seleccionado</p>
        </article>

        <article className="accounting-kpi-card">
          <div className="accounting-kpi-icon icon-danger"><ReceiptText size={20} /></div>
          <span className="accounting-kpi-label">Salidas</span>
          <strong className="accounting-kpi-value">{money.format(balance?.salidas ?? 0)}</strong>
          <p>Egresos acumulados del periodo</p>
        </article>

        <article className="accounting-kpi-card">
          <div className="accounting-kpi-icon icon-warning"><DollarSign size={20} /></div>
          <span className="accounting-kpi-label">Balance</span>
          <strong className="accounting-kpi-value">{money.format(balance?.balance ?? 0)}</strong>
          <p>Resultado neto del rango</p>
        </article>

        <article className="accounting-kpi-card accounting-kpi-card-accent">
          <div className="accounting-kpi-icon icon-soft"><Wallet size={20} /></div>
          <span className="accounting-kpi-label">Movimientos</span>
          <strong className="accounting-kpi-value">{entriesCount}</strong>
          <p>Registros totales en el filtro actual</p>
        </article>
      </section>

      <section className="accounting-layout">
        <div className="accounting-main-column">
          <article className="card accounting-panel accounting-cash-panel">
            <div className="accounting-panel-header">
              <div>
                <h3>Caja diaria</h3>
                <p>Abre la jornada, controla el efectivo esperado y cierra al finalizar el turno.</p>
              </div>
              <span className={`chip ${caja ? '' : 'accounting-chip-muted'}`}>{caja ? 'Activa' : 'Pendiente'}</span>
            </div>

            {!caja ? (
              <div className="accounting-open-box">
                <div className="accounting-helper-card">
                  <strong>Antes de registrar movimientos</strong>
                  <p>Define el monto inicial de caja. Esto habilita ingresos, egresos y el cálculo de cierre esperado.</p>
                </div>

                <div className="entity-form">
                  <label>
                    <span>Monto de apertura</span>
                    <input type="number" min="0" step="0.01" value={montoApertura} onChange={(event) => setMontoApertura(event.target.value)} />
                  </label>
                  <button type="button" onClick={abrirCaja} disabled={!montoApertura}>
                    <LockOpen size={18} />
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
                    <input type="number" min="0" step="0.01" value={montoCierre} onChange={(event) => setMontoCierre(event.target.value)} />
                  </label>
                  <label>
                    <span>Observaciones</span>
                    <textarea value={observacionesCierre} onChange={(event) => setObservacionesCierre(event.target.value)} placeholder="Diferencias, retiros, pagos o notas del turno..." />
                  </label>
                  <button type="button" className="secondary accounting-close-button" onClick={cerrarCaja} disabled={!montoCierre}>
                    <DollarSign size={18} />
                    Cerrar caja
                  </button>
                </div>
              </div>
            )}
          </article>

          <article className="card accounting-panel accounting-table-panel">
            <div className="accounting-panel-header">
              <div>
                <h3>Movimientos registrados</h3>
                <p>Listado cronológico paginado de ingresos y egresos con el filtro de fecha actual.</p>
              </div>
              <span className="chip">{entriesCount} movimientos</span>
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
                        <th>Categoría</th>
                        <th>Descripción</th>
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
                    Página {entriesPage.number + 1} de {Math.max(entriesPage.totalPages || 1, 1)}
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
        </div>

        <aside className="accounting-side-column">
          <article className="card accounting-panel accounting-filter-panel">
            <div className="accounting-panel-header">
              <div>
                <h3>Balance por rango</h3>
                <p>Consulta rápidamente cómo se comporta el flujo de caja por fechas.</p>
              </div>
            </div>

            <div className="form-grid two-columns">
              <label>
                <span>Fecha inicio</span>
                <input type="date" value={range.fechaInicio} onChange={(event) => setRange({ ...range, fechaInicio: event.target.value })} />
              </label>
              <label>
                <span>Fecha fin</span>
                <input type="date" value={range.fechaFin} onChange={(event) => setRange({ ...range, fechaFin: event.target.value })} />
              </label>
            </div>

            <button type="button" onClick={applyBalanceFilter}>
              <CalendarRange size={18} />
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

          <article className="card accounting-panel accounting-guide-panel">
            <span className="accounting-guide-kicker">Flujo recomendado</span>
            <h3>Cómo usar esta pantalla</h3>
            <ol className="accounting-flow-list">
              <li>Abre la caja con el monto inicial del turno.</li>
              <li>Registra cada entrada o salida desde el botón superior.</li>
              <li>Consulta el balance del rango cuando necesites revisar resultados.</li>
              <li>Cierra la caja al final del día con el monto real y observaciones.</li>
            </ol>
          </article>
        </aside>
      </section>

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
