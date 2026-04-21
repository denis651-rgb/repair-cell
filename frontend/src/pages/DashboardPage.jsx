import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  ArrowRight,
  ClipboardList,
  Coins,
  CreditCard,
  Package,
  RefreshCcw,
  ShoppingBag,
  ShoppingCart,
  TrendingDown,
  TrendingUp,
  Users,
  Wallet,
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
  Legend,
} from 'recharts';
import { api } from '../api/api';
import { money } from '../utils/formatters';
import '../styles/pages/dashboard.css';

const PIE_COLORS = ['#0f766e', '#dc2626', '#f59e0b', '#2563eb', '#7c3aed', '#475569'];

function mergeDailySeries(panel) {
  const grouped = new Map();

  const appendValues = (items = [], field) => {
    items.forEach((item) => {
      const current = grouped.get(item.fecha) || {
        fecha: item.fecha,
        ingresos: 0,
        egresos: 0,
        operaciones: 0,
      };

      current[field] = Number(item.valor || 0);
      grouped.set(item.fecha, current);
    });
  };

  appendValues(panel?.ingresosPorDia, 'ingresos');
  appendValues(panel?.egresosPorDia, 'egresos');
  appendValues(panel?.operacionesPorDia, 'operaciones');

  return Array.from(grouped.values())
    .sort((left, right) => left.fecha.localeCompare(right.fecha))
    .map((item) => ({
      ...item,
      balance: item.ingresos - item.egresos,
    }));
}

export default function DashboardPage() {
  const [panel, setPanel] = useState(null);
  const [ordenes, setOrdenes] = useState([]);
  const [caja, setCaja] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const loadDashboard = async () => {
    setLoading(true);
    setError('');
    try {
      const [panelData, ordenesData, cajaData] = await Promise.all([
        api.get('/reportes/panel-global'),
        api.get('/ordenes-reparacion'),
        api.get('/contabilidad/caja/actual'),
      ]);

      const ordenesOrdenadas = [...(ordenesData || [])]
        .sort((left, right) => String(right.recibidoEn || '').localeCompare(String(left.recibidoEn || '')));

      setPanel(panelData);
      setOrdenes(ordenesOrdenadas.slice(0, 6));
      setCaja(cajaData);
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
    () => (panel?.estadosOrden || []).map((item, index) => ({ ...item, fill: PIE_COLORS[index % PIE_COLORS.length] })),
    [panel],
  );

  const serieFinanciera = useMemo(() => mergeDailySeries(panel), [panel]);
  const productosCriticos = panel?.productosStockBajo || [];
  const cajaActual = Number(caja?.saldoActual ?? caja?.montoApertura ?? 0);

  if (loading && !panel) {
    return <div className="card empty-state">Cargando dashboard global...</div>;
  }

  return (
    <div className="page-stack dashboard-page">
      <section className="dashboard-hero">
        <div>
          <span className="dashboard-kicker">Panel del negocio</span>
          <h1>Operacion, caja y comercio en una sola vista</h1>
          <p>
            Ahora el dashboard separa lo operativo del taller, lo financiero y lo comercial para que podamos leer el negocio completo sin mezclar senales.
          </p>
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
                <span className="dashboard-kicker">Lectura rapida</span>
                <h2>{panel.balanceNeto >= 0 ? 'El negocio viene en balance positivo' : 'Atencion al balance del periodo'}</h2>
                <p>
                  {panel.balanceNeto >= 0
                    ? 'Los ingresos reales superan a los egresos reales segun contabilidad y el panel global.'
                    : 'Los egresos reales estan por encima de los ingresos y conviene revisar compras, salidas manuales y cobranzas.'}
                </p>
              </div>
              <div className={`dashboard-highlight-badge ${panel.balanceNeto >= 0 ? 'is-safe' : 'is-danger'}`}>
                <Coins size={18} />
                {money.format(panel.balanceNeto || 0)}
              </div>
            </article>

            <article className="dashboard-caja-card">
              <span className="dashboard-kicker">Caja actual</span>
              <h3>{caja ? 'Caja abierta' : 'Caja cerrada'}</h3>
              <p>
                {caja
                  ? `Saldo visible ${money.format(cajaActual)}`
                  : 'Abre una caja para que el seguimiento diario de efectivo quede reflejado aqui.'}
              </p>
            </article>
          </section>

          <section className="dashboard-section">
            <div className="dashboard-section-heading">
              <div>
                <span className="dashboard-kicker">Bloque operativo</span>
                <h2>Clientes, ordenes y reparaciones</h2>
              </div>
              <Link to="/reparaciones" className="text-link">Ir a reparaciones <ArrowRight size={14} /></Link>
            </div>

            <div className="dashboard-kpi-grid">
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
                <p>Ordenes acumuladas en el taller</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-warning"><Wrench size={20} /></div>
                <div className="dash-card-label">Pendientes</div>
                <div className="dash-card-value">{panel.ordenesPendientes}</div>
                <p>Equipos aun no entregados</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-danger"><AlertCircle size={20} /></div>
                <div className="dash-card-label">Estados activos</div>
                <div className="dash-card-value">{estados.length}</div>
                <p>Distribucion actual del flujo de reparacion</p>
              </article>
            </div>

            <div className="dashboard-grid-2">
              <article className="card card-pad chart-card dashboard-panel">
                <div className="section-title-row">
                  <h3>Estado de reparaciones</h3>
                  <span className="chip">Operacion</span>
                </div>
                <div className="chart-wrap">
                  <ResponsiveContainer width="100%" height={280}>
                    <PieChart>
                      <Pie data={estados} dataKey="cantidad" nameKey="estado" outerRadius={90} innerRadius={54} paddingAngle={3}>
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

              <article className="card card-pad dashboard-panel">
                <div className="section-title-row">
                  <h3>Ordenes recientes</h3>
                  <span className="chip">Ultimas 6</span>
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
                      {ordenes.length === 0 && (
                        <tr>
                          <td colSpan="4" className="table-empty-cell">No hay ordenes para mostrar.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </article>
            </div>
          </section>

          <section className="dashboard-section">
            <div className="dashboard-section-heading">
              <div>
                <span className="dashboard-kicker">Bloque financiero</span>
                <h2>Ingresos, egresos y cobranzas reales</h2>
              </div>
              <span className="chip chip-strong">Balance {money.format(panel.balanceNeto || 0)}</span>
            </div>

            <div className="dashboard-kpi-grid dashboard-kpi-grid-financial">
              <article className="dashboard-kpi-card dashboard-kpi-card-income">
                <div className="card-icon-box icon-safe"><TrendingUp size={20} /></div>
                <div className="dash-card-label">Ingresos reales</div>
                <div className="dash-card-value">{money.format(panel.ingresosTotales || 0)}</div>
                <p>Entradas contables acumuladas</p>
              </article>

              <article className="dashboard-kpi-card dashboard-kpi-card-expense">
                <div className="card-icon-box icon-danger"><TrendingDown size={20} /></div>
                <div className="dash-card-label">Egresos reales</div>
                <div className="dash-card-value">{money.format(panel.egresosTotales || 0)}</div>
                <p>Salidas contables acumuladas</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-soft"><Wallet size={20} /></div>
                <div className="dash-card-label">Caja actual</div>
                <div className="dash-card-value">{money.format(cajaActual)}</div>
                <p>{caja ? 'Saldo visible en caja abierta' : 'Sin caja activa ahora mismo'}</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-warning"><CreditCard size={20} /></div>
                <div className="dash-card-label">Cobranzas pendientes</div>
                <div className="dash-card-value">{money.format(panel.saldoPendienteCobro || 0)}</div>
                <p>{panel.cuentasPorCobrarAbiertas} cuentas por cobrar abiertas</p>
              </article>
            </div>

            <div className="dashboard-grid-2">
              <article className="card card-pad chart-card dashboard-panel">
                <div className="section-title-row">
                  <h3>Flujo financiero por dia</h3>
                  <span className="chip">Contabilidad</span>
                </div>
                <div className="chart-wrap">
                  <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={serieFinanciera}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="fecha" tick={{ fontSize: 11 }} />
                      <YAxis />
                      <Tooltip formatter={(value) => money.format(Number(value || 0))} />
                      <Legend />
                      <Line type="monotone" dataKey="ingresos" name="Ingresos" stroke="#0f766e" strokeWidth={3} dot={false} />
                      <Line type="monotone" dataKey="egresos" name="Egresos" stroke="#dc2626" strokeWidth={3} dot={false} />
                      <Line type="monotone" dataKey="balance" name="Balance" stroke="#1d4ed8" strokeWidth={3} dot={false} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </article>

              <article className="card card-pad chart-card dashboard-panel">
                <div className="section-title-row">
                  <h3>Origen de ingresos y egresos</h3>
                  <span className="chip">Desglose</span>
                </div>
                <div className="dashboard-finance-breakdown">
                  <div className="dashboard-finance-breakdown-item">
                    <span>Ingresos por reparaciones</span>
                    <strong>{money.format(panel.ingresosReparaciones || 0)}</strong>
                  </div>
                  <div className="dashboard-finance-breakdown-item">
                    <span>Ingresos por ventas</span>
                    <strong>{money.format(panel.ingresosVentas || 0)}</strong>
                  </div>
                  <div className="dashboard-finance-breakdown-item">
                    <span>Cobros de credito</span>
                    <strong>{money.format(panel.cobrosCredito || 0)}</strong>
                  </div>
                  <div className="dashboard-finance-breakdown-item">
                    <span>Egresos por compras</span>
                    <strong>{money.format(panel.egresosCompras || 0)}</strong>
                  </div>
                  <div className="dashboard-finance-breakdown-item">
                    <span>Egresos manuales</span>
                    <strong>{money.format(panel.egresosManuales || 0)}</strong>
                  </div>
                </div>
              </article>
            </div>
          </section>

          <section className="dashboard-section">
            <div className="dashboard-section-heading">
              <div>
                <span className="dashboard-kicker">Bloque comercial</span>
                <h2>Ventas, compras y stock critico</h2>
              </div>
              <Link to="/inventario" className="text-link">Ver productos criticos <ArrowRight size={14} /></Link>
            </div>

            <div className="dashboard-kpi-grid">
              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-soft"><ShoppingBag size={20} /></div>
                <div className="dash-card-label">Ventas totales</div>
                <div className="dash-card-value">{panel.totalVentas}</div>
                <p>Documentos de venta registrados</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-soft"><ShoppingCart size={20} /></div>
                <div className="dash-card-label">Compras totales</div>
                <div className="dash-card-value">{panel.totalCompras}</div>
                <p>Reposiciones y abastecimiento registrados</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-danger"><Package size={20} /></div>
                <div className="dash-card-label">Stock bajo</div>
                <div className="dash-card-value">{panel.inventarioBajo}</div>
                <p>Productos en o bajo su minimo</p>
              </article>

              <article className="dashboard-kpi-card">
                <div className="card-icon-box icon-warning"><Coins size={20} /></div>
                <div className="dash-card-label">Operaciones</div>
                <div className="dash-card-value">{serieFinanciera.reduce((sum, item) => sum + Number(item.operaciones || 0), 0)}</div>
                <p>Movimientos financieros del panel</p>
              </article>
            </div>

            <div className="dashboard-grid-2">
              <article className="card card-pad chart-card dashboard-panel">
                <div className="section-title-row">
                  <h3>Actividad por dia</h3>
                  <span className="chip">Volumen</span>
                </div>
                <div className="chart-wrap">
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={serieFinanciera}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="fecha" tick={{ fontSize: 11 }} />
                      <YAxis allowDecimals={false} />
                      <Tooltip />
                      <Bar dataKey="operaciones" name="Operaciones" radius={[10, 10, 0, 0]} fill="#f59e0b" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </article>

              <article className="card card-pad dashboard-panel dashboard-stock-panel">
                <div className="section-title-row">
                  <h3>Productos criticos</h3>
                  <span className="chip">Top {productosCriticos.length}</span>
                </div>
                <div className="stock-list dashboard-stock-list">
                  {productosCriticos.length === 0 ? (
                    <div className="empty-stock-state">
                      <Package size={28} />
                      <h4>Sin alertas criticas</h4>
                      <p>Todos los productos tienen stock suficiente.</p>
                    </div>
                  ) : (
                    productosCriticos.map((producto) => {
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
            </div>
          </section>
        </>
      )}
    </div>
  );
}
