import { useMemo, useState } from 'react';
import { NavLink, Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  Smartphone,
  Wrench,
  Package,
  Calculator,
  BarChart3,
  DatabaseBackup,
  Search,
  Bell,
  Menu,
  Plus,
  X,
  Sparkles,
  LogOut,
} from 'lucide-react';
import yiyoTecMark from '../assets/yiyo-tec-mark.svg';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/clientes', label: 'Clientes', icon: Users },
  { to: '/dispositivos', label: 'Dispositivos', icon: Smartphone },
  { to: '/reparaciones', label: 'Ordenes', icon: Wrench },
  { to: '/inventario', label: 'Inventario', icon: Package },
  { to: '/contabilidad', label: 'Contabilidad', icon: Calculator },
  { to: '/reportes', label: 'Reportes', icon: BarChart3 },
  { to: '/respaldos', label: 'Respaldos', icon: DatabaseBackup },
];

export default function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const location = useLocation();
  const navigate = useNavigate();

  const pageMeta = useMemo(() => {
    if (location.pathname.includes('/clientes')) {
      return {
        placeholder: 'Buscar clientes o telefonos',
        title: 'Clientes',
        description: 'Seguimiento claro de contactos y equipos asociados.',
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
        description: 'Control visual de repuestos, herramientas y suministros.',
      };
    }

    if (location.pathname.includes('/contabilidad')) {
      return {
        placeholder: 'Buscar movimientos, caja o referencias',
        title: 'Contabilidad',
        description: 'Caja diaria, ingresos y egresos con mejor trazabilidad.',
      };
    }

    if (location.pathname.includes('/reportes')) {
      return {
        placeholder: 'Buscar metricas, rangos o resultados',
        title: 'Reportes',
        description: 'Panorama comercial y tecnico del taller.',
      };
    }

    if (location.pathname.includes('/respaldos')) {
      return {
        placeholder: 'Buscar respaldos, carpetas o estados',
        title: 'Respaldos',
        description: 'Copias locales y sincronizacion remota de la base de datos.',
      };
    }

    return {
      placeholder: 'Buscar en el sistema',
      title: 'Dashboard',
      description: 'Resumen general para operar el taller con claridad.',
    };
  }, [location.pathname]);

  const enviarBusqueda = (event) => {
    event.preventDefault();
    if (!busqueda.trim()) return;
    navigate(`/reparaciones?busqueda=${encodeURIComponent(busqueda.trim())}`);
    setMenuOpen(false);
  };

  const cerrarSesion = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setMenuOpen(false);
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      {menuOpen && (
        <button
          className="sidebar-backdrop"
          onClick={() => setMenuOpen(false)}
          aria-label="Cerrar menu"
        />
      )}

      <aside className={`sidebar ${menuOpen ? 'open' : ''}`}>
        <div className="brand brand-row">
          <div className="brand-mark-shell">
            <img src={yiyoTecMark} alt="Yiyo Tec" className="brand-mark" />
          </div>
          <div className="brand-copy">
            <h2>Yiyo Tec</h2>
            <p>Phone &amp; Computer Repair</p>
            <span className="brand-tagline">Precision tecnica para cada ingreso</span>
          </div>
          <button className="icon-btn mobile-close" onClick={() => setMenuOpen(false)} aria-label="Cerrar menu lateral">
            <X size={20} />
          </button>
        </div>

        <div className="sidebar-section-label">Operacion diaria</div>

        <nav className="nav-links">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
                onClick={() => setMenuOpen(false)}
              >
                <span className="nav-link-icon">
                  <Icon size={18} strokeWidth={2} />
                </span>
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="sidebar-bottom">
          <div className="sidebar-highlight-card">
            <div className="sidebar-highlight-icon">
              <Sparkles size={18} />
            </div>
            <div>
              <strong>Flujo mas ordenado</strong>
              <p>Usa busqueda global y accesos rapidos para moverte entre modulos.</p>
            </div>
          </div>

          <Link to="/reparaciones">
            <button className="btn-new-order">
              <Plus size={18} />
              <span>Nueva orden</span>
            </button>
          </Link>

          <div className="user-profile">
            <div className="user-avatar avatar-fallback">YT</div>
            <div className="user-info">
              <h4>Administrador</h4>
              <p>Yiyo Tec principal</p>
            </div>
          </div>

          <button type="button" className="sidebar-logout-button" onClick={cerrarSesion}>
            <LogOut size={18} />
            <span>Cerrar sesion</span>
          </button>
        </div>
      </aside>

      <div className="main-wrapper">
        <header className="top-bar">
          <div className="top-bar-left">
            <button className="icon-btn mobile-only" onClick={() => setMenuOpen(true)} aria-label="Abrir menu">
              <Menu size={22} />
            </button>

            <div className="top-bar-copy">
              <span className="eyebrow">Panel Yiyo Tec</span>
              <strong>{pageMeta.title}</strong>
              <p>{pageMeta.description}</p>
            </div>

            <form className="search-container" onSubmit={enviarBusqueda}>
              <Search size={16} color="#5b657d" />
              <input
                type="text"
                placeholder={pageMeta.placeholder}
                value={busqueda}
                onChange={(event) => setBusqueda(event.target.value)}
              />
            </form>
          </div>

          <div className="top-bar-actions">
            <button className="icon-btn notification-btn" aria-label="Notificaciones">
              <Bell size={20} />
              <span className="notification-dot" />
            </button>
            <div className="workspace-info">
              <h5>Panel operativo</h5>
              <p>Sesion local</p>
            </div>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
