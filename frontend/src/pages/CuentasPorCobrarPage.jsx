import { useEffect, useMemo, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { api } from '../api/api';
import EmptyState from '../components/common/EmptyState';
import Modal from '../components/common/Modal';
import PageHeader from '../components/PageHeader';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { toDateInputValue } from '../utils/formatters';
import '../styles/pages/cuentas-por-cobrar.css';

const TAMANO = 8;
const paginaVacia = { content: [], totalPages: 0, totalElements: 0, number: 0 };
const abonoInicial = {
  monto: '',
  fechaAbono: toDateInputValue(),
  observaciones: '',
};

export default function CuentasPorCobrarPage() {
  const [cuentasPage, setCuentasPage] = useState(paginaVacia);
  const [busqueda, setBusqueda] = useState('');
  const [estadoFiltro, setEstadoFiltro] = useState('');
  const [pagina, setPagina] = useState(0);
  const [modalAbonoOpen, setModalAbonoOpen] = useState(false);
  const [cuentaSeleccionada, setCuentaSeleccionada] = useState(null);
  const [abonoForm, setAbonoForm] = useState(abonoInicial);
  const [error, setError] = useState('');

  const busquedaDebounced = useDebouncedValue(busqueda, 250);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const cargarCuentas = async (paginaObjetivo = pagina) => {
    try {
      setError('');
      const respuesta = await api.get('/cuentas-por-cobrar/paginado', {
        pagina: paginaObjetivo,
        tamano: TAMANO,
        busqueda: busquedaDebounced,
        estado: estadoFiltro || undefined,
      });
      setCuentasPage(respuesta || paginaVacia);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    setPagina(0);
  }, [busquedaDebounced, estadoFiltro]);

  useEffect(() => {
    cargarCuentas(pagina);
  }, [pagina, busquedaDebounced, estadoFiltro]);

  const abrirAbono = (cuenta) => {
    setCuentaSeleccionada(cuenta);
    setAbonoForm({
      monto: String(Number(cuenta?.saldoPendiente || 0)),
      fechaAbono: toDateInputValue(),
      observaciones: '',
    });
    setModalAbonoOpen(true);
  };

  const guardarAbono = async (event) => {
    event.preventDefault();
    if (!cuentaSeleccionada) return;

    try {
      await api.post(`/cuentas-por-cobrar/${cuentaSeleccionada.id}/abonos`, {
        ...abonoForm,
        monto: Number(abonoForm.monto),
      });
      setModalAbonoOpen(false);
      setCuentaSeleccionada(null);
      setAbonoForm(abonoInicial);
      await cargarCuentas(pagina);
    } catch (err) {
      setError(err.message);
    }
  };

  const cuentas = cuentasPage.content || [];

  return (
    <div className="page-stack receivables-page">
      <PageHeader title="Cuentas por cobrar" subtitle="Control de ventas al credito, saldos pendientes y cobros parciales.">
        <div className="inventory-header-actions">
          <select value={estadoFiltro} onChange={(event) => setEstadoFiltro(event.target.value)} className="receivables-state-filter">
            <option value="">Todos los estados</option>
            <option value="PENDIENTE">Pendiente</option>
            <option value="PARCIAL">Parcial</option>
            <option value="PAGADA">Pagada</option>
            <option value="ANULADA">Anulada</option>
          </select>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <section className="inventory-hero-card receivables-toolbar-card">
        <div className="inventory-toolbar">
          <label className="inventory-search">
            <Search size={16} />
            <input value={busqueda} onChange={(event) => setBusqueda(event.target.value)} placeholder="Buscar por cliente, telefono o referencia de venta" />
          </label>
        </div>
      </section>

      <section className="card receivables-table-card">
        <div className="inventory-panel-header">
          <div>
            <h3>Listado de cuentas por cobrar</h3>
            <p>Los abonos registran entrada contable y reducen el saldo pendiente.</p>
          </div>
          <span className="chip">{cuentasPage.totalElements} registros</span>
        </div>

        {cuentas.length === 0 ? (
          <EmptyState title="Sin cuentas por cobrar" description="Las ventas a credito apareceran aqui automaticamente." />
        ) : (
          <>
            <div className="receivables-list">
              {cuentas.map((cuenta) => (
                <article key={cuenta.id} className="receivable-card">
                  <div className="receivable-card-header">
                    <div>
                      <strong>{cuenta.cliente?.nombreCompleto || 'Cliente'}</strong>
                      <p>{cuenta.venta?.numeroComprobante || `Venta #${cuenta.venta?.id || cuenta.id}`}</p>
                    </div>
                    <span className={cuenta.estado === 'ANULADA' ? 'badge badge-danger' : 'chip'}>{cuenta.estado}</span>
                  </div>

                  <div className="receivable-card-grid">
                    <span>Emitida: {cuenta.fechaEmision}</span>
                    <span>Monto original: Bs {currency.format(Number(cuenta.montoOriginal || 0))}</span>
                    <span>Saldo: Bs {currency.format(Number(cuenta.saldoPendiente || 0))}</span>
                  </div>

                  {cuenta.abonos?.length > 0 && (
                    <div className="receivable-payments">
                      {cuenta.abonos.slice(0, 3).map((abono) => (
                        <span key={abono.id} className="purchase-item-chip">
                          Abono {abono.fechaAbono} • Bs {currency.format(Number(abono.monto || 0))}
                        </span>
                      ))}
                    </div>
                  )}

                  {(cuenta.estado === 'PENDIENTE' || cuenta.estado === 'PARCIAL') && (
                    <div className="sale-card-actions">
                      <button className="secondary compact" onClick={() => abrirAbono(cuenta)}>
                        <Plus size={14} />
                        Registrar abono
                      </button>
                    </div>
                  )}
                </article>
              ))}
            </div>

            <div className="pagination-row inventory-pagination-row">
              <button className="secondary" type="button" disabled={pagina <= 0} onClick={() => setPagina((actual) => Math.max(actual - 1, 0))}>
                Anterior
              </button>
              <span>
                Pagina {cuentasPage.number + 1} de {Math.max(cuentasPage.totalPages || 1, 1)}
              </span>
              <button className="secondary" type="button" disabled={pagina + 1 >= (cuentasPage.totalPages || 1)} onClick={() => setPagina((actual) => actual + 1)}>
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>

      <Modal
        open={modalAbonoOpen}
        onClose={() => {
          setModalAbonoOpen(false);
          setCuentaSeleccionada(null);
          setAbonoForm(abonoInicial);
        }}
        title="Registrar abono"
        subtitle="El abono reduce el saldo y crea una entrada contable automatica."
      >
        <form className="entity-form" onSubmit={guardarAbono}>
          <label>
            <span>Monto</span>
            <input type="number" min="0" step="0.01" value={abonoForm.monto} onChange={(event) => setAbonoForm((actual) => ({ ...actual, monto: event.target.value }))} required />
          </label>
          <label>
            <span>Fecha de abono</span>
            <input type="date" value={abonoForm.fechaAbono} onChange={(event) => setAbonoForm((actual) => ({ ...actual, fechaAbono: event.target.value }))} />
          </label>
          <label>
            <span>Observaciones</span>
            <textarea value={abonoForm.observaciones} onChange={(event) => setAbonoForm((actual) => ({ ...actual, observaciones: event.target.value }))} />
          </label>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalAbonoOpen(false)}>Cancelar</button>
            <button type="submit">Guardar abono</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
