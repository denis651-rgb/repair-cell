import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  CalendarDays,
  ClipboardList,
  CircleDollarSign,
  Clock3,
  Download,
  FileSpreadsheet,
  Package,
  ShoppingBag,
  ShoppingCart,
  TrendingDown,
  TrendingUp,
  UserRound,
  Wrench,
} from 'lucide-react';
import * as XLSX from 'xlsx';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import { money, toDateInputValue } from '../utils/formatters';
import '../styles/pages/reportes.css';

const hoy = toDateInputValue();
const hace7 = toDateInputValue(new Date(Date.now() - 6 * 24 * 60 * 60 * 1000));
const ESTADO_COLORS = ['#0f766e', '#dc2626', '#f59e0b', '#2563eb', '#7c3aed', '#475569'];
const BLOQUES_INICIALES = {
  financiero: null,
  clientes: null,
  panelGlobal: null,
  panelOperativo: null,
  ordenes: null,
};

function diffDays(inicio, fin) {
  const from = new Date(inicio);
  const to = new Date(fin);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) {
    return null;
  }

  return Math.max(0, Math.round((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24)));
}

function logFrontendRequestError({ endpoint, params, error, contexto }) {
  console.error(`[Reportes] ${contexto}`, {
    endpoint,
    params,
    mensajeBackend: error?.message || 'Sin mensaje',
    error,
  });
}

function construirErrorBloque(titulo, endpoint, params, error) {
  logFrontendRequestError({ endpoint, params, error, contexto: titulo });
  return {
    titulo,
    detalle: error?.message || 'No se pudo cargar este bloque del tablero.',
  };
}

export default function ReportesPage() {
  const [rango, setRango] = useState({ inicio: hace7, fin: hoy });
  const [serieFinanciera, setSerieFinanciera] = useState([]);
  const [clientesGlobales, setClientesGlobales] = useState([]);
  const [panelGlobal, setPanelGlobal] = useState(null);
  const [panelOperativo, setPanelOperativo] = useState(null);
  const [ordenes, setOrdenes] = useState([]);
  const [bloquesError, setBloquesError] = useState(BLOQUES_INICIALES);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const cargarTodo = async (inicio = rango.inicio, fin = rango.fin) => {
    setLoading(true);
    setError('');
    setBloquesError(BLOQUES_INICIALES);

    const solicitudes = [
      {
        key: 'financiero',
        endpoint: '/reportes/financiero-por-fecha',
        params: { inicio, fin },
        request: api.get('/reportes/financiero-por-fecha', { inicio, fin }),
      },
      {
        key: 'clientes',
        endpoint: '/reportes/clientes-global',
        params: {},
        request: api.get('/reportes/clientes-global'),
      },
      {
        key: 'panelGlobal',
        endpoint: '/reportes/panel-global',
        params: {},
        request: api.get('/reportes/panel-global'),
      },
      {
        key: 'panelOperativo',
        endpoint: '/reportes/panel',
        params: {},
        request: api.get('/reportes/panel'),
      },
      {
        key: 'ordenes',
        endpoint: '/ordenes-reparacion',
        params: {},
        request: api.get('/ordenes-reparacion'),
      },
    ];

    const resultados = await Promise.allSettled(solicitudes.map((solicitud) => solicitud.request));
    const errores = { ...BLOQUES_INICIALES };

    resultados.forEach((resultado, index) => {
      const solicitud = solicitudes[index];
      if (resultado.status === 'fulfilled') {
        const valor = resultado.value;
        if (solicitud.key === 'financiero') setSerieFinanciera(valor || []);
        if (solicitud.key === 'clientes') setClientesGlobales(valor || []);
        if (solicitud.key === 'panelGlobal') setPanelGlobal(valor || null);
        if (solicitud.key === 'panelOperativo') setPanelOperativo(valor || null);
        if (solicitud.key === 'ordenes') setOrdenes(valor || []);
        return;
      }

      errores[solicitud.key] = construirErrorBloque(
        `No se pudo cargar ${solicitud.endpoint}.`,
        solicitud.endpoint,
        solicitud.params,
        resultado.reason,
      );

      if (solicitud.key === 'financiero') setSerieFinanciera([]);
      if (solicitud.key === 'clientes') setClientesGlobales([]);
      if (solicitud.key === 'panelGlobal') setPanelGlobal(null);
      if (solicitud.key === 'panelOperativo') setPanelOperativo(null);
      if (solicitud.key === 'ordenes') setOrdenes([]);
    });

    setBloquesError(errores);

    const bloquesFallidos = Object.values(errores).filter(Boolean);
    if (bloquesFallidos.length > 0) {
      setError(
        bloquesFallidos.length === solicitudes.length
          ? 'No se pudo cargar ningun bloque de reportes.'
          : `Algunos bloques fallaron (${bloquesFallidos.length}). El resto del tablero sigue disponible.`,
      );
    }

    setLoading(false);
  };

  useEffect(() => {
    cargarTodo();
  }, []);

  const resumenFinanciero = useMemo(() => {
    return serieFinanciera.reduce(
      (acc, item) => ({
        ingresos: acc.ingresos + Number(item.ingresos || 0),
        egresos: acc.egresos + Number(item.egresos || 0),
        balance: acc.balance + Number(item.balance || 0),
        ventas: acc.ventas + Number(item.ventas || 0),
        compras: acc.compras + Number(item.compras || 0),
        reparaciones: acc.reparaciones + Number(item.reparaciones || 0),
      }),
      { ingresos: 0, egresos: 0, balance: 0, ventas: 0, compras: 0, reparaciones: 0 },
    );
  }, [serieFinanciera]);

  const mejorCliente = clientesGlobales[0];
  const bloquesConError = useMemo(
    () => Object.entries(bloquesError).filter(([, value]) => Boolean(value)),
    [bloquesError],
  );

  const estadosOperativos = useMemo(
    () => (panelOperativo?.estados || []).map((item, index) => ({
      ...item,
      fill: ESTADO_COLORS[index % ESTADO_COLORS.length],
    })),
    [panelOperativo],
  );

  const tiemposReparacion = useMemo(() => {
    const cerradas = (ordenes || [])
      .filter((orden) => orden.recibidoEn && orden.entregadoEn)
      .map((orden) => ({
        id: orden.id,
        numeroOrden: orden.numeroOrden,
        cliente: orden.cliente?.nombreCompleto || 'Sin cliente',
        dias: diffDays(orden.recibidoEn, orden.entregadoEn),
      }))
      .filter((orden) => orden.dias !== null)
      .sort((left, right) => right.dias - left.dias);

    const totalDias = cerradas.reduce((sum, orden) => sum + orden.dias, 0);
    const promedioDias = cerradas.length ? totalDias / cerradas.length : 0;
    const maximoDias = cerradas.length ? cerradas[0].dias : 0;

    return {
      cerradas,
      promedioDias,
      maximoDias,
      cantidadCerradas: cerradas.length,
      topLentas: cerradas.slice(0, 8),
    };
  }, [ordenes]);

  const exportarExcel = () => {
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(serieFinanciera), 'Financiero');
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(clientesGlobales), 'Clientes global');
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(panelGlobal?.productosStockBajo || []), 'Stock critico');
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(estadosOperativos), 'Estados orden');
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(tiemposReparacion.cerradas), 'Tiempos reparacion');
    XLSX.writeFile(workbook, `reportes-negocio-${hoy}.xlsx`);
  };

  return (
    <div className="page-stack reports-page">
      <PageHeader
        title="Reportes del negocio"
        subtitle="Separamos la lectura financiera, comercial y operativa para que los reportes cuenten una historia clara y util del taller."
      >
        <div className="reports-header-actions">
          <button className="secondary reports-ghost-button" onClick={() => cargarTodo(rango.inicio, rango.fin)}>
            <CalendarDays size={18} />
            Actualizar
          </button>
          <button className="reports-primary-button" onClick={exportarExcel}>
            <FileSpreadsheet size={18} />
            Exportar Excel
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      {bloquesConError.length > 0 && (
        <section className="reports-status-grid">
          {bloquesConError.map(([key, value]) => (
            <article key={key} className="reports-status-card reports-status-card-danger">
              <strong>{value.titulo}</strong>
              <p>{value.detalle}</p>
            </article>
          ))}
        </section>
      )}

      <section className="reports-hero-card">
        <div className="reports-hero-copy">
          <span className="reports-kicker">Vision global</span>
          <h2>{loading ? 'Armando reportes...' : 'Tres vistas para leer el negocio sin mezclar metricas'}</h2>
          <p>
            El panel de reportes ahora separa finanzas, comercio e indicadores operativos. Asi es mas facil detectar si el problema viene de caja, ventas, compras o reparaciones.
          </p>
        </div>
        <div className="reports-hero-badge-grid">
          <span className="reports-hero-badge"><TrendingUp size={18} /> {money.format(resumenFinanciero.ingresos)}</span>
          <span className="reports-hero-badge reports-hero-badge-danger"><TrendingDown size={18} /> {money.format(resumenFinanciero.egresos)}</span>
        </div>
      </section>

      <section className="reports-filter-card">
        <div className="reports-filter-grid">
          <label>
            <span>Inicio</span>
            <input type="date" value={rango.inicio} onChange={(event) => setRango({ ...rango, inicio: event.target.value })} />
          </label>
          <label>
            <span>Fin</span>
            <input type="date" value={rango.fin} onChange={(event) => setRango({ ...rango, fin: event.target.value })} />
          </label>
          <button onClick={() => cargarTodo(rango.inicio, rango.fin)}>
            <Download size={18} />
            Generar reporte
          </button>
        </div>
        <div className="reports-filter-summary">
          <div>
            <span>Periodo actual</span>
            <strong>{rango.inicio} - {rango.fin}</strong>
          </div>
          <div>
            <span>Balance del periodo</span>
            <strong>{money.format(resumenFinanciero.balance)}</strong>
          </div>
        </div>
      </section>

      <section className="reports-nav-cards">
        <article className="reports-nav-card">
          <CircleDollarSign size={20} />
          <div>
            <strong>Reporte financiero</strong>
            <p>Ingresos, egresos y balance por fecha.</p>
          </div>
        </article>
        <article className="reports-nav-card">
          <ShoppingBag size={20} />
          <div>
            <strong>Reporte comercial</strong>
            <p>Ventas, compras, clientes y stock critico.</p>
          </div>
        </article>
        <article className="reports-nav-card">
          <Wrench size={20} />
          <div>
            <strong>Reporte operativo</strong>
            <p>Estados de orden y tiempos de reparacion.</p>
          </div>
        </article>
      </section>

      <section className="reports-section">
        <div className="reports-section-header">
          <div>
            <span className="reports-kicker">Reporte financiero</span>
            <h2>Ingresos, egresos y balance por fecha</h2>
          </div>
        </div>

        {bloquesError.financiero && (
          <div className="reports-block-alert" role="alert">
            <div>
              <strong>No se pudo cargar el bloque financiero.</strong>
              <p>{bloquesError.financiero.detalle}</p>
            </div>
            <button type="button" className="secondary reports-ghost-button" onClick={() => cargarTodo(rango.inicio, rango.fin)}>
              Reintentar
            </button>
          </div>
        )}

        <section className="reports-kpi-grid">
          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-safe"><TrendingUp size={20} /></div>
            <span className="reports-kpi-label">Ingresos del periodo</span>
            <strong className="reports-kpi-value reports-kpi-value-money">{money.format(resumenFinanciero.ingresos)}</strong>
            <p>Entradas contables dentro del rango</p>
          </article>

          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-danger"><TrendingDown size={20} /></div>
            <span className="reports-kpi-label">Egresos del periodo</span>
            <strong className="reports-kpi-value reports-kpi-value-money">{money.format(resumenFinanciero.egresos)}</strong>
            <p>Salidas contables dentro del rango</p>
          </article>

          <article className="reports-kpi-card reports-kpi-card-accent">
            <div className="reports-kpi-icon icon-soft"><BarChart3 size={20} /></div>
            <span className="reports-kpi-label">Balance neto</span>
            <strong className="reports-kpi-value reports-kpi-value-money">{money.format(resumenFinanciero.balance)}</strong>
            <p>Diferencia entre ingresos y egresos</p>
          </article>

          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-warning"><CalendarDays size={20} /></div>
            <span className="reports-kpi-label">Dias reportados</span>
            <strong className="reports-kpi-value">{serieFinanciera.length}</strong>
            <p>Fechas incluidas en el rango actual</p>
          </article>
        </section>

        <section className="reports-grid-2">
          <article className="card card-pad chart-card reports-panel">
            <div className="section-title-row">
              <h3>Flujo financiero diario</h3>
              <span className="chip">Contabilidad</span>
            </div>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={serieFinanciera}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="fecha" />
                  <YAxis />
                  <Tooltip formatter={(value) => money.format(Number(value || 0))} />
                  <Line type="monotone" dataKey="ingresos" stroke="#0f766e" strokeWidth={3} dot={false} />
                  <Line type="monotone" dataKey="egresos" stroke="#dc2626" strokeWidth={3} dot={false} />
                  <Line type="monotone" dataKey="balance" stroke="#2563eb" strokeWidth={3} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </article>

          <article className="card card-pad chart-card reports-panel">
            <div className="section-title-row">
              <h3>Origen del movimiento</h3>
              <span className="chip">Ventas, compras y reparaciones</span>
            </div>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={serieFinanciera}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="fecha" />
                  <YAxis />
                  <Tooltip formatter={(value) => money.format(Number(value || 0))} />
                  <Bar dataKey="ventas" fill="#0f766e" radius={[10, 10, 0, 0]} />
                  <Bar dataKey="compras" fill="#f59e0b" radius={[10, 10, 0, 0]} />
                  <Bar dataKey="reparaciones" fill="#2563eb" radius={[10, 10, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </article>
        </section>
      </section>

      <section className="reports-section">
        <div className="reports-section-header">
          <div>
            <span className="reports-kicker">Reporte comercial</span>
            <h2>Ventas, compras, clientes y stock critico</h2>
          </div>
        </div>

        {(bloquesError.clientes || bloquesError.panelGlobal) && (
          <div className="reports-block-alert" role="alert">
            <div>
              <strong>No se pudo cargar todo el bloque comercial.</strong>
              <p>{bloquesError.clientes?.detalle || bloquesError.panelGlobal?.detalle}</p>
            </div>
            <button type="button" className="secondary reports-ghost-button" onClick={() => cargarTodo(rango.inicio, rango.fin)}>
              Reintentar
            </button>
          </div>
        )}

        <section className="reports-kpi-grid">
          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-soft"><ShoppingBag size={20} /></div>
            <span className="reports-kpi-label">Ventas del periodo</span>
            <strong className="reports-kpi-value reports-kpi-value-money">{money.format(resumenFinanciero.ventas)}</strong>
            <p>Ingresos asociados a ventas</p>
          </article>

          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-warning"><ShoppingCart size={20} /></div>
            <span className="reports-kpi-label">Compras del periodo</span>
            <strong className="reports-kpi-value reports-kpi-value-money">{money.format(resumenFinanciero.compras)}</strong>
            <p>Salidas asociadas a compras</p>
          </article>

          <article className="reports-kpi-card reports-kpi-card-accent">
            <div className="reports-kpi-icon icon-soft"><UserRound size={20} /></div>
            <span className="reports-kpi-label">Top cliente</span>
            <strong className="reports-kpi-value reports-kpi-value-compact">{mejorCliente?.cliente || 'Sin datos'}</strong>
            <p>{mejorCliente ? money.format(mejorCliente.totalConsumidoGlobal || 0) : 'Sin consumo registrado'}</p>
          </article>

          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-danger"><Package size={20} /></div>
            <span className="reports-kpi-label">Stock critico</span>
            <strong className="reports-kpi-value">{panelGlobal?.inventarioBajo || 0}</strong>
            <p>Productos en o bajo su minimo</p>
          </article>
        </section>

        <section className="reports-grid-2">
          <article className="card reports-table-card">
            <div className="reports-table-header">
              <div>
                <h3>Clientes globales</h3>
                <p>Consumo total mezclando reparaciones, ventas y pendientes de cobro.</p>
              </div>
              <span className="chip">{clientesGlobales.length} clientes</span>
            </div>
            <div className="responsive-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Cliente</th>
                    <th>Reparaciones</th>
                    <th>Ventas</th>
                    <th>Total</th>
                    <th>Pendiente</th>
                  </tr>
                </thead>
                <tbody>
                  {clientesGlobales.map((fila) => (
                    <tr key={fila.clienteId}>
                      <td>{fila.cliente}</td>
                      <td>{money.format(fila.totalReparaciones || 0)}</td>
                      <td>{money.format(fila.totalVentas || 0)}</td>
                      <td>{money.format(fila.totalConsumidoGlobal || 0)}</td>
                      <td>{money.format(fila.saldoPendiente || 0)}</td>
                    </tr>
                  ))}
                  {clientesGlobales.length === 0 && (
                    <tr>
                      <td colSpan="5" className="table-empty-cell">No hay clientes para mostrar.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </article>

          <article className="card reports-table-card">
            <div className="reports-table-header">
              <div>
                <h3>Stock critico</h3>
                <p>Productos que ya necesitan reposicion o seguimiento cercano.</p>
              </div>
              <span className="chip">{panelGlobal?.productosStockBajo?.length || 0} productos</span>
            </div>
            <div className="responsive-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Stock</th>
                    <th>Minimo</th>
                  </tr>
                </thead>
                <tbody>
                  {(panelGlobal?.productosStockBajo || []).map((producto) => (
                    <tr key={producto.id}>
                      <td>{producto.nombre}</td>
                      <td>{producto.cantidadStock}</td>
                      <td>{producto.stockMinimo}</td>
                    </tr>
                  ))}
                  {(panelGlobal?.productosStockBajo || []).length === 0 && (
                    <tr>
                      <td colSpan="3" className="table-empty-cell">No hay productos criticos.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </article>
        </section>
      </section>

      <section className="reports-section">
        <div className="reports-section-header">
          <div>
            <span className="reports-kicker">Reporte operativo</span>
            <h2>Ordenes por estado y tiempos de reparacion</h2>
          </div>
        </div>

        {(bloquesError.panelOperativo || bloquesError.ordenes) && (
          <div className="reports-block-alert" role="alert">
            <div>
              <strong>No se pudo cargar todo el bloque operativo.</strong>
              <p>{bloquesError.panelOperativo?.detalle || bloquesError.ordenes?.detalle}</p>
            </div>
            <button type="button" className="secondary reports-ghost-button" onClick={() => cargarTodo(rango.inicio, rango.fin)}>
              Reintentar
            </button>
          </div>
        )}

        <section className="reports-kpi-grid">
          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-soft"><ClipboardList size={20} /></div>
            <span className="reports-kpi-label">Ordenes totales</span>
            <strong className="reports-kpi-value">{panelOperativo?.totalOrdenes || 0}</strong>
            <p>Ordenes registradas en el sistema</p>
          </article>

          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-warning"><Wrench size={20} /></div>
            <span className="reports-kpi-label">Pendientes</span>
            <strong className="reports-kpi-value">{panelOperativo?.ordenesPendientes || 0}</strong>
            <p>Equipos aun no entregados</p>
          </article>

          <article className="reports-kpi-card reports-kpi-card-accent">
            <div className="reports-kpi-icon icon-soft"><Clock3 size={20} /></div>
            <span className="reports-kpi-label">Promedio reparacion</span>
            <strong className="reports-kpi-value">{tiemposReparacion.promedioDias.toFixed(1)} d</strong>
            <p>Promedio sobre ordenes cerradas</p>
          </article>

          <article className="reports-kpi-card">
            <div className="reports-kpi-icon icon-danger"><Clock3 size={20} /></div>
            <span className="reports-kpi-label">Caso mas lento</span>
            <strong className="reports-kpi-value">{tiemposReparacion.maximoDias} d</strong>
            <p>{tiemposReparacion.cantidadCerradas} ordenes cerradas medidas</p>
          </article>
        </section>

        <section className="reports-grid-2">
          <article className="card card-pad chart-card reports-panel">
            <div className="section-title-row">
              <h3>Ordenes por estado</h3>
              <span className="chip">Flujo actual</span>
            </div>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie data={estadosOperativos} dataKey="cantidad" nameKey="estado" outerRadius={96} innerRadius={56} paddingAngle={3}>
                    {estadosOperativos.map((entry) => <Cell key={entry.estado} fill={entry.fill} />)}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="legend-grid">
              {estadosOperativos.map((item) => (
                <div key={item.estado} className="legend-item">
                  <span className="legend-dot" style={{ background: item.fill }} />
                  <span>{item.estado}</span>
                  <strong>{item.cantidad}</strong>
                </div>
              ))}
            </div>
          </article>

          <article className="card card-pad chart-card reports-panel">
            <div className="section-title-row">
              <h3>Tiempos de reparacion</h3>
              <span className="chip">Top 8 mas lentas</span>
            </div>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={tiemposReparacion.topLentas}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="numeroOrden" hide />
                  <YAxis allowDecimals={false} />
                  <Tooltip formatter={(value) => `${value} dias`} />
                  <Bar dataKey="dias" fill="#2563eb" radius={[10, 10, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </article>
        </section>
      </section>
    </div>
  );
}
