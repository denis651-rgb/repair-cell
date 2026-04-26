import { useMemo, useState } from 'react';
import { NavLink, Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import {
  BadgeDollarSign,
  BarChart3,
  Calculator,
  ChevronDown,
  DatabaseBackup,
  HandCoins,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Package,
  Plus,
  Search,
  ShoppingCart,
  Smartphone,
  Sparkles,
  Sun,
  Truck,
  Users,
  Wrench,
  X,
} from 'lucide-react';
import yiyoTecMark from '../assets/yiyo-tec-mark.svg';
import { useTheme } from '../context/ThemeContext';
import { clearStoredSession, getCurrentUser, hasPermission } from '../utils/permissions';

const navSections = [
  {
    id: 'operacion',
    label: 'Operacion',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, permission: 'DASHBOARD_VIEW' },
      { to: '/clientes', label: 'Clientes', icon: Users, permission: 'CLIENTES_VIEW' },
      { to: '/dispositivos', label: 'Dispositivos', icon: Smartphone, permission: 'DISPOSITIVOS_VIEW' },
      { to: '/reparaciones', label: 'Ordenes', icon: Wrench, permission: 'REPARACIONES_VIEW' },
    ],
  },
  {
    id: 'comercial',
    label: 'Comercial',
    items: [
      { to: '/inventario', label: 'Inventario', icon: Package, permission: 'INVENTARIO_VIEW' },
      { to: '/proveedores', label: 'Proveedores', icon: Truck, permission: 'PROVEEDORES_VIEW' },
      { to: '/compras', label: 'Compras', icon: ShoppingCart, permission: 'COMPRAS_VIEW' },
      { to: '/ventas', label: 'Ventas', icon: BadgeDollarSign, permission: 'VENTAS_VIEW' },
      { to: '/cuentas-por-cobrar', label: 'Cuentas por cobrar', icon: HandCoins, permission: 'CUENTAS_POR_COBRAR_VIEW' },
    ],
  },
  {
    id: 'finanzas',
    label: 'Finanzas y control',
    items: [
      { to: '/contabilidad', label: 'Contabilidad', icon: Calculator, permission: 'CONTABILIDAD_VIEW' },
      { to: '/reportes', label: 'Reportes', icon: BarChart3, permission: 'REPORTES_VIEW' },
      { to: '/respaldos', label: 'Respaldos', icon: DatabaseBackup, permission: 'BACKUPS_VIEW' },
    ],
  },
];

const initialSections = navSections.reduce((acc, section) => {
  acc[section.id] = true;
  return acc;
}, {});

export default function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [seccionesAbiertas, setSeccionesAbiertas] = useState(initialSections);
  const location = useLocation();
  const navigate = useNavigate();
  const { theme, isDark, toggleTheme } = useTheme();
  const currentUser = getCurrentUser();
  const visibleSections = useMemo(() => navSections
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.permission || hasPermission(currentUser, item.permission)),
    }))
    .filter((section) => section.items.length > 0), [currentUser]);

  const pageMeta = useMemo(() => {
    const canViewDashboard = hasPermission(currentUser, 'DASHBOARD_VIEW');
    const canViewAccounting = hasPermission(currentUser, 'CONTABILIDAD_VIEW');
    const canViewReports = hasPermission(currentUser, 'REPORTES_VIEW');
    const canViewBackups = hasPermission(currentUser, 'BACKUPS_VIEW');

    if (location.pathname.includes('/clientes')) {
      return {
        placeholder: 'Buscar clientes o telefonos',
        title: 'Clientes',
        description: 'Seguimiento claro de contactos y relacion comercial.',
      };
    }
    if (location.pathname.includes('/dispositivos')) {
      return {
        placeholder: 'Buscar equipos, IMEI o modelos',
        title: 'Dispositivos',
        description: 'Ficha tecnica y control rapido de equipos recibidos.',
      };
    }
    if (location.pathname.includes('/reparaciones')) {
      return {
        placeholder: 'Buscar ordenes, clientes o diagnosticos',
        title: 'Ordenes',
        description: 'Vista operativa para coordinar el trabajo del taller.',
      };
    }
    if (location.pathname.includes('/inventario')) {
      return {
        placeholder: 'Buscar productos, stock o categorias',
        title: 'Inventario',
        description: 'Control comercial de repuestos, marcas y existencias.',
      };
    }
    if (location.pathname.includes('/proveedores')) {
      return {
        placeholder: 'Buscar proveedores, ciudades o telefonos',
        title: 'Proveedores',
        description: 'Base institucional de abastecimiento del negocio.',
      };
    }
    if (location.pathname.includes('/compras')) {
      return {
        placeholder: 'Buscar compras, proveedores o comprobantes',
        title: 'Compras',
        description: 'Ingreso de mercaderia con impacto automatico en inventario.',
      };
    }
    if (location.pathname.includes('/ventas')) {
      return {
        placeholder: 'Buscar ventas, clientes o comprobantes',
        title: 'Ventas',
        description: 'Venta directa de productos con trazabilidad comercial.',
      };
    }
    if (location.pathname.includes('/cuentas-por-cobrar')) {
      return {
        placeholder: 'Buscar cuentas, clientes o referencias',
        title: 'Cuentas por cobrar',
        description: 'Seguimiento de creditos, abonos y saldos pendientes.',
      };
    }
    if (location.pathname.includes('/contabilidad') && canViewAccounting) {
      return {
        placeholder: 'Buscar movimientos, categorias o modulos',
        title: 'Contabilidad',
        description: 'Caja diaria y movimientos automaticos por modulo.',
      };
    }
    if (location.pathname.includes('/reportes') && canViewReports) {
      return {
        placeholder: 'Buscar metricas, rangos o resultados',
        title: 'Reportes',
        description: 'Lectura gerencial y tecnica del desempeño del taller.',
      };
    }
    if (location.pathname.includes('/respaldos') && canViewBackups) {
      return {
        placeholder: 'Buscar respaldos, carpetas o estados',
        title: 'Respaldos',
        description: 'Continuidad operativa y resguardo de datos del sistema.',
      };
    }
    return {
      placeholder: 'Buscar en el sistema',
      title: canViewDashboard ? 'Dashboard' : 'Panel operativo',
      description: canViewDashboard
        ? 'Resumen institucional del negocio y su operacion.'
        : 'Vista de trabajo filtrada segun los permisos del usuario actual.',
    };
  }, [currentUser, location.pathname]);

  const enviarBusqueda = (event) => {
    event.preventDefault();
    if (!busqueda.trim()) return;
    navigate(`/reparaciones?busqueda=${encodeURIComponent(busqueda.trim())}`);
    setMenuOpen(false);
  };

  const cerrarSesion = () => {
    clearStoredSession();
    setMenuOpen(false);
    navigate('/login', { replace: true });
  };

  const toggleSection = (sectionId) => {
    setSeccionesAbiertas((actual) => ({
      ...actual,
      [sectionId]: !actual[sectionId],
    }));
  };

  return (
    <div className="app-shell institutional-shell">
      {menuOpen && (
        <button className="sidebar-backdrop" onClick={() => setMenuOpen(false)} aria-label="Cerrar menu" />
      )}

      <aside className={`sidebar institutional-sidebar ${menuOpen ? 'open' : ''}`}>
        <div className="brand brand-row">
          <div className="brand-mark-shell">
            <img src={yiyoTecMark} alt="Yiyo Tec" className="brand-mark" />
          </div>
          <div className="brand-copy">
            <h2>Yiyo Tec</h2>
            <p>Phone &amp; Computer Repair</p>
            <span className="brand-tagline">Gestion integrada de taller y repuestos</span>
          </div>
          <button className="icon-btn mobile-close" onClick={() => setMenuOpen(false)} aria-label="Cerrar menu lateral">
            <X size={18} />
          </button>
        </div>

        <div className="sidebar-section-label">Navegacion por modulos</div>

        <nav className="module-nav">
          {visibleSections.map((section) => {
            const activa = section.items.some((item) => location.pathname.startsWith(item.to));
            const abierta = seccionesAbiertas[section.id];

            return (
              <div key={section.id} className={`module-nav-group ${activa ? 'is-active' : ''}`}>
                <button
                  type="button"
                  className="module-nav-toggle"
                  onClick={() => toggleSection(section.id)}
                  aria-expanded={abierta}
                >
                  <span>{section.label}</span>
                  <ChevronDown size={16} className={`module-nav-chevron ${abierta ? 'is-open' : ''}`} />
                </button>

                {abierta && (
                  <div className="nav-links module-nav-links">
                    {section.items.map((item) => {
                      const Icon = item.icon;
                      return (
                        <NavLink
                          key={item.to}
                          to={item.to}
                          className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
                          onClick={() => setMenuOpen(false)}
                        >
                          <span className="nav-link-icon">
                            <Icon size={16} strokeWidth={2} />
                          </span>
                          <span>{item.label}</span>
                        </NavLink>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        <div className="sidebar-bottom">
          <div className="sidebar-highlight-card institutional-highlight-card">
            <div className="sidebar-highlight-icon">
              <Sparkles size={16} />
            </div>
            <div>
              <strong>Navegacion modular</strong>
              <p>Agrupa las paginas por area para que el sistema siga creciendo sin saturar el menu.</p>
            </div>
          </div>

          <Link to="/reparaciones">
            <button className="btn-new-order compact">
              <Plus size={16} />
              <span>Nueva orden</span>
            </button>
          </Link>
        </div>
      </aside>

      <div className="main-wrapper">
        <header className="top-bar institutional-top-bar">
          <div className="top-bar-left">
            <button className="icon-btn mobile-only" onClick={() => setMenuOpen(true)} aria-label="Abrir menu">
              <Menu size={20} />
            </button>

            <div className="top-bar-copy">
              <span className="eyebrow">Panel institucional</span>
              <strong>{pageMeta.title}</strong>
              <p>{pageMeta.description}</p>
            </div>

            <form className="search-container institutional-search" onSubmit={enviarBusqueda}>
              <Search size={14} color="#607276" />
              <input
                type="text"
                placeholder={pageMeta.placeholder}
                value={busqueda}
                onChange={(event) => setBusqueda(event.target.value)}
              />
            </form>
          </div>

          <div className="top-bar-actions">
            <button
              type="button"
              className={`theme-toggle ${isDark ? 'is-dark' : 'is-light'}`}
              onClick={toggleTheme}
              aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              aria-pressed={isDark}
              title={isDark ? 'Modo oscuro activo' : 'Modo claro activo'}
            >
              <span className="theme-toggle-thumb" aria-hidden="true" />
              <span className={`theme-toggle-option ${theme === 'light' ? 'is-active' : ''}`} aria-hidden="true">
                <Sun size={15} />
              </span>
              <span className={`theme-toggle-option ${theme === 'dark' ? 'is-active' : ''}`} aria-hidden="true">
                <Moon size={15} />
              </span>
            </button>
            <button type="button" className="topbar-logout-button icon-only" onClick={cerrarSesion} aria-label="Cerrar sesion" title="Cerrar sesion">
              <LogOut size={16} />
            </button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
