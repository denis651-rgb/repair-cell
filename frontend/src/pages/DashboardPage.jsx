import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  ArrowRight,
  ClipboardList,
  Package,
  RefreshCcw,
  TrendingUp,
  Users,
  Wrench,
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  CartesianGrid,
  XAxis,
  YAxis,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
} from 'recharts';
import { api } from '../api/api';
import { money } from '../utils/formatters';
import '../styles/pages/dashboard.css';

const PIE_COLORS = ['#5b3df5', '#0f8f54', '#f97316', '#e11d48', '#0891b2'];

export default function DashboardPage() {
  const [panel, setPanel] = useState(null);
  const [ordenes, setOrdenes] = useState([]);
  const [caja, setCaja] = useState(null);
  const [lowStockProducts, setLowStockProducts] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const loadDashboard = async () => {
    setLoading(true);
    setError('');
    try {
      const [panelData, ordenesData, cajaData, lowStockData] = await Promise.all([
        api.get('/reportes/panel'),
        api.get('/ordenes-reparacion'),
        api.get('/contabilidad/caja/actual'),
        api.get('/inventario/productos/stock-bajo'),
      ]);

      setPanel(panelData);
      setOrdenes((ordenesData || []).slice(0, 6));
      setCaja(cajaData);
      setLowStockProducts(lowStockData || []);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);

  const estados = useMemo(
    () => (panel?.estados || []).map((item, index) => ({ ...item, fill: PIE_COLORS[index % PIE_COLORS.length] })),
    [panel],
  );

  const lowStockCount = lowStockProducts.length;

  if (loading && !panel) {
    return <div className="card empty-state">Cargando dashboard...</div>;
  }

  return (
    <div className="page-stack dashboard-page">
      <section className="dashboard-hero">
        <div>
          <span className="dashboard-kicker">Panel operativo</span>
          <h1>Dashboard del taller</h1>
          <p>Resumen del negocio, estado de reparaciones, caja activa e inventario critico en tiempo real.</p>
        </div>
        <div className="dashboard-hero-actions">
          <button className="secondary dashboard-refresh-button" type="button" onClick={loadDashboard}>
            <RefreshCcw size={18} />
            Actualizar
          </button>
          <Link to="/inventario" className="dashboard-link-button">
            Revisar inventario
            <ArrowRight size={16} />
          </Link>
        </div>
      </section>

      {error && <div className="alert">{error}</div>}

      {panel && (
        <>
          <section className="dashboard-summary-strip">
            <article className="dashboard-highlight-card">
              <div className="dashboard-highlight-copy">
                <span className="dashboard-kicker">Inventario real</span>
                <h2>{lowStockCount === 0 ? 'Todo bajo control' : `${lowStockCount} alertas activas de stock`}</h2>
                <p>
                  {lowStockCount === 0
                    ? 'No hay productos por debajo del minimo configurado.'
                    : 'La alerta del dashboard ahora se alimenta del inventario actual para evitar diferencias con la vista de productos.'}
                </p>
              </div>
              <div className={`dashboard-highlight-badge ${lowStockCount > 0 ? 'is-danger' : 'is-safe'}`}>
                <Package size={18} />
                {lowStockCount > 0 ? `${lowStockCount} con stock bajo` : 'Sin alertas'}
              </div>
            </article>

            <article className="dashboard-caja-card">
              <span className="dashboard-kicker">Caja</span>
              <h3>{caja ? 'Caja abierta' : 'Caja cerrada'}</h3>
              <p>{caja ? `Monto de apertura ${money.format(caja.montoApertura || 0)}` : 'Abre una caja para registrar movimientos contables.'}</p>
            </article>
          </section>

          <section className="dashboard-kpi-grid">
            <article className="dashboard-kpi-card">
              <div className="card-icon-box icon-soft"><Users size={20} /></div>
              <div className="dash-card-label">Clientes</div>
              <div className="dash-card-value">{panel.totalClientes}</div>
              <p>Base actual de clientes registrados</p>
            </article>

            <article className="dashboard-kpi-card">
              <div className="card-icon-box icon-soft"><ClipboardList size={20} /></div>
              <div className="dash-card-label">Ordenes</div>
              <div className="dash-card-value">{panel.totalOrdenes}</div>
              <p>Ordenes acumuladas en el sistema</p>
            </article>

            <article className="dashboard-kpi-card">
              <div className="card-icon-box icon-warning"><Wrench size={20} /></div>
              <div className="dash-card-label">Pendientes</div>
              <div className="dash-card-value">{panel.ordenesPendientes}</div>
              <p>Ordenes aun no entregadas</p>
            </article>

            <article className="dashboard-kpi-card">
              <div className="card-icon-box icon-danger"><AlertCircle size={20} /></div>
              <div className="dash-card-label">Stock bajo</div>
              <div className="dash-card-value">{lowStockCount}</div>
              <p>Conteo sincronizado con inventario</p>
            </article>
          </section>

          <section className="dashboard-grid-2">
            <article className="card card-pad chart-card dashboard-panel">
              <div className="section-title-row">
                <h3>Ordenes por dia</h3>
                <span className="chip">Historico</span>
              </div>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={panel.ordenesPorDia}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="fecha" tick={{ fontSize: 11 }} />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="valor" radius={[10, 10, 0, 0]} fill="#5b3df5" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </article>

            <article className="card card-pad chart-card dashboard-panel">
              <div className="section-title-row">
                <h3>Ingresos por dia</h3>
                <span className="chip">Acumulado</span>
              </div>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={280}>
                  <LineChart data={panel.ingresosPorDia}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="fecha" tick={{ fontSize: 11 }} />
                    <YAxis />
                    <Tooltip formatter={(value) => money.format(value)} />
                    <Line type="monotone" dataKey="valor" stroke="#0f8f54" strokeWidth={3} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </article>
          </section>

          <section className="dashboard-grid-2">
            <article className="card card-pad chart-card dashboard-panel">
              <div className="section-title-row">
                <h3>Estado de reparaciones</h3>
                <span className="chip">Distribucion</span>
              </div>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={280}>
                  <PieChart>
                    <Pie data={estados} dataKey="cantidad" nameKey="estado" outerRadius={90} innerRadius={52} paddingAngle={3}>
                      {estados.map((entry) => <Cell key={entry.estado} fill={entry.fill} />)}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="legend-grid">
                {estados.map((item) => (
                  <div key={item.estado} className="legend-item">
                    <span className="legend-dot" style={{ background: item.fill }} />
                    <span>{item.estado}</span>
                    <strong>{item.cantidad}</strong>
                  </div>
                ))}
              </div>
            </article>

            <article className="card card-pad dashboard-panel dashboard-stock-panel">
              <div className="section-title-row">
                <h3>Alerta de inventario</h3>
                <Link to="/inventario" className="text-link">Ir a inventario <ArrowRight size={14} /></Link>
              </div>
              <div className="stock-list dashboard-stock-list">
                {lowStockProducts.length === 0 ? (
                  <div className="empty-stock-state">
                    <Package size={28} />
                    <h4>Sin alertas criticas</h4>
                    <p>Todos los productos tienen stock suficiente.</p>
                  </div>
                ) : (
                  lowStockProducts.slice(0, 8).map((producto) => {
                    const stockActual = Number(producto.cantidadStock ?? producto.stockActual ?? 0);
                    const stockMinimo = Number(producto.stockMinimo ?? 0);
                    const ratio = Math.max(8, Math.min(100, Math.round((stockActual / Math.max(stockMinimo, 1)) * 100)));

                    return (
                      <div key={producto.id} className="dashboard-stock-item">
                        <div className="dashboard-stock-copy">
                          <strong>{producto.nombre}</strong>
                          <p>Stock {stockActual} / minimo {stockMinimo}</p>
                          <div className="dashboard-stock-track">
                            <div className="dashboard-stock-fill" style={{ width: `${ratio}%` }} />
                          </div>
                        </div>
                        <span className="badge badge-danger">{stockActual}</span>
                      </div>
                    );
                  })
                )}
              </div>
            </article>
          </section>

          <section className="dashboard-grid-2">
            <article className="card card-pad metric-banner dashboard-panel dashboard-income-panel">
              <div className="metric-banner-icon"><TrendingUp size={26} /></div>
              <div>
                <div className="muted">Ingresos registrados</div>
                <h2 className="dashboard-income-value">{money.format(panel.ingresosTotales || 0)}</h2>
              </div>
            </article>

            <article className="card card-pad dashboard-panel">
              <div className="section-title-row">
                <h3>Ordenes recientes</h3>
                <Link to="/reparaciones" className="text-link">Ver todas</Link>
              </div>
              <div className="responsive-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Numero</th>
                      <th>Cliente</th>
                      <th>Estado</th>
                      <th>Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ordenes.map((orden) => (
                      <tr key={orden.id}>
                        <td>{orden.numeroOrden}</td>
                        <td>{orden.cliente?.nombreCompleto}</td>
                        <td><span className="badge">{orden.estado}</span></td>
                        <td>{money.format(orden.costoFinal || 0)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>
          </section>
        </>
      )}
    </div>
  );
}
