import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  CalendarDays,
  Download,
  FileSpreadsheet,
  TrendingUp,
  UserRound,
  Wrench,
} from 'lucide-react';
import * as XLSX from 'xlsx';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import { money } from '../utils/formatters';
import '../styles/pages/reportes.css';

const hoy = new Date().toISOString().slice(0, 10);
const hace7 = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

export default function ReportesPage() {
  const [rango, setRango] = useState({ inicio: hace7, fin: hoy });
  const [serieFecha, setSerieFecha] = useState([]);
  const [porCliente, setPorCliente] = useState([]);
  const [porTecnico, setPorTecnico] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const cargarTodo = async (inicio = rango.inicio, fin = rango.fin) => {
    setLoading(true);
    try {
      setError('');
      const [fecha, clientes, tecnicos] = await Promise.all([
        api.get('/reportes/por-fecha', { inicio, fin }),
        api.get('/reportes/por-cliente'),
        api.get('/reportes/por-tecnico'),
      ]);
      setSerieFecha(fecha || []);
      setPorCliente(clientes || []);
      setPorTecnico(tecnicos || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargarTodo();
  }, []);

  const totalPeriodo = useMemo(
    () => serieFecha.reduce((sum, item) => sum + Number(item.valor || 0), 0),
    [serieFecha],
  );

  const promedioPeriodo = useMemo(
    () => (serieFecha.length ? totalPeriodo / serieFecha.length : 0),
    [serieFecha.length, totalPeriodo],
  );

  const mejorCliente = porCliente[0];
  const mejorTecnico = porTecnico[0];

  const exportarExcel = () => {
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(serieFecha), 'Por fecha');
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(porCliente), 'Por cliente');
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(porTecnico), 'Por técnico');
    XLSX.writeFile(workbook, `reportes-taller-${hoy}.xlsx`);
  };

  return (
    <div className="page-stack reports-page">
      <PageHeader title="Reportes" subtitle="Visualiza tendencias del taller, compara clientes y técnicos, y exporta resúmenes listos para gestión.">
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

      <section className="reports-hero-card">
        <div className="reports-hero-copy">
          <span className="reports-kicker">Resumen analítico</span>
          <h2>{loading ? 'Generando reportes...' : 'Mide el rendimiento del taller con una vista más clara'}</h2>
          <p>Filtra por fechas, revisa el comportamiento diario y detecta rápidamente los clientes y técnicos con mayor facturación.</p>
        </div>
        <div className="reports-hero-badge">
          <BarChart3 size={20} />
          {serieFecha.length} registros del período
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
            <span>Período actual</span>
            <strong>{rango.inicio} - {rango.fin}</strong>
          </div>
          <div>
            <span>Ingreso acumulado</span>
            <strong>{money.format(totalPeriodo)}</strong>
          </div>
        </div>
      </section>

      <section className="reports-kpi-grid">
        <article className="reports-kpi-card">
          <div className="reports-kpi-icon icon-soft"><CalendarDays size={20} /></div>
          <span className="reports-kpi-label">Registros del período</span>
          <strong className="reports-kpi-value">{serieFecha.length}</strong>
          <p>Días con información en el rango</p>
        </article>

        <article className="reports-kpi-card">
          <div className="reports-kpi-icon icon-warning"><TrendingUp size={20} /></div>
          <span className="reports-kpi-label">Ingreso del período</span>
          <strong className="reports-kpi-value reports-kpi-value-money">{money.format(totalPeriodo)}</strong>
          <p>Facturación total dentro del rango</p>
        </article>

        <article className="reports-kpi-card reports-kpi-card-accent">
          <div className="reports-kpi-icon icon-soft"><BarChart3 size={20} /></div>
          <span className="reports-kpi-label">Promedio diario</span>
          <strong className="reports-kpi-value reports-kpi-value-money">{money.format(promedioPeriodo)}</strong>
          <p>Promedio sobre los registros del período</p>
        </article>

        <article className="reports-kpi-card">
          <div className="reports-kpi-icon icon-danger"><UserRound size={20} /></div>
          <span className="reports-kpi-label">Clientes / Técnicos</span>
          <strong className="reports-kpi-value">{porCliente.length} / {porTecnico.length}</strong>
          <p>Participantes en el análisis actual</p>
        </article>
      </section>

      <section className="reports-summary-grid">
        <article className="reports-summary-card">
          <span className="reports-kicker">Top cliente</span>
          <h3>{mejorCliente?.cliente || 'Sin datos'}</h3>
          <p>{mejorCliente ? `${mejorCliente.totalOrdenes} órdenes • ${money.format(mejorCliente.totalFacturado || 0)}` : 'No hay clientes para mostrar.'}</p>
        </article>

        <article className="reports-summary-card reports-summary-card-alt">
          <span className="reports-kicker">Top técnico</span>
          <h3>{mejorTecnico?.tecnico || 'Sin datos'}</h3>
          <p>{mejorTecnico ? `${mejorTecnico.totalOrdenes} órdenes • ${money.format(mejorTecnico.totalFacturado || 0)}` : 'No hay técnicos para mostrar.'}</p>
        </article>
      </section>

      <section className="reports-grid-2">
        <article className="card card-pad chart-card reports-panel">
          <div className="section-title-row">
            <h3>Reporte por fecha</h3>
            <span className="chip">Serie diaria</span>
          </div>
          <div className="chart-wrap">
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={serieFecha}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="fecha" />
                <YAxis />
                <Tooltip formatter={(value) => money.format(value)} />
                <Line type="monotone" dataKey="valor" stroke="#5b3df5" strokeWidth={3} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </article>

        <article className="card card-pad chart-card reports-panel">
          <div className="section-title-row">
            <h3><UserRound size={18} /> Facturación por cliente</h3>
            <span className="chip">Top 8</span>
          </div>
          <div className="chart-wrap">
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={porCliente.slice(0, 8)}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="cliente" hide />
                <YAxis />
                <Tooltip formatter={(value) => money.format(value)} />
                <Bar dataKey="totalFacturado" radius={[10, 10, 0, 0]} fill="#5b3df5" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </article>
      </section>

      <section className="reports-grid-2">
        <article className="card reports-table-card">
          <div className="reports-table-header">
            <div>
              <h3>Reporte por cliente</h3>
              <p>Ranking de clientes por volumen de órdenes y facturación.</p>
            </div>
            <span className="chip">{porCliente.length} clientes</span>
          </div>
          <div className="responsive-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Cliente</th>
                  <th>Órdenes</th>
                  <th>Total facturado</th>
                </tr>
              </thead>
              <tbody>
                {porCliente.map((fila) => (
                  <tr key={fila.clienteId}>
                    <td>{fila.cliente}</td>
                    <td>{fila.totalOrdenes}</td>
                    <td>{money.format(fila.totalFacturado || 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </article>

        <article className="card reports-table-card">
          <div className="reports-table-header">
            <div>
              <h3><Wrench size={18} /> Reporte por técnico</h3>
              <p>Comparativa del rendimiento de técnicos por órdenes y facturación.</p>
            </div>
            <span className="chip">{porTecnico.length} técnicos</span>
          </div>
          <div className="responsive-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Técnico</th>
                  <th>Órdenes</th>
                  <th>Total facturado</th>
                </tr>
              </thead>
              <tbody>
                {porTecnico.map((fila) => (
                  <tr key={fila.tecnico}>
                    <td>{fila.tecnico}</td>
                    <td>{fila.totalOrdenes}</td>
                    <td>{money.format(fila.totalFacturado || 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </article>
      </section>
    </div>
  );
}
