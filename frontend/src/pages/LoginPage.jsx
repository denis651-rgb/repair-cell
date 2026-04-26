import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  BarChart3,
  Boxes,
  Eye,
  EyeOff,
  LockKeyhole,
  ShieldCheck,
  UserRound,
  Wrench,
} from 'lucide-react';
import { api } from '../api/api';
import yiyoTecMark from '../assets/yiyo-tec-mark.svg';
import '../styles/pages/login.css';
import { getCurrentUser, getDefaultRouteForUser } from '../utils/permissions';

export default function LoginPage() {
  const token = localStorage.getItem('token');
  const currentUser = getCurrentUser();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mostrarContrasena, setMostrarContrasena] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  if (token && currentUser?.rol) {
    return <Navigate to={getDefaultRouteForUser(currentUser)} replace />;
  }

  const handleLogin = async (event) => {
    event.preventDefault();

    const usernameLimpio = username.trim();

    if (!usernameLimpio || !password.trim()) {
      setError('Ingresa tu usuario y contraseña para continuar.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const data = await api.post('/auth/login', {
        username: usernameLimpio,
        password,
      });

      const usuarioSesion = {
        username: data.username,
        nombre: data.nombre,
        rol: data.rol,
        permisos: Array.isArray(data.permisos) ? data.permisos : [],
      };

      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(usuarioSesion));
      localStorage.setItem('usuarioActual', JSON.stringify(usuarioSesion));

      navigate(getDefaultRouteForUser(usuarioSesion), { replace: true });
    } catch (err) {
      setError(err.message || 'No fue posible iniciar sesión.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <div className="login-bg-orb login-bg-orb--one" />
      <div className="login-bg-orb login-bg-orb--two" />
      <div className="login-bg-grid" />

      <div className="login-layout">
        <section className="login-showcase" aria-label="Presentación del sistema">
          <div className="login-brand-block">
            <div className="login-brand-badge">Panel administrativo</div>

            <div className="login-brand-row">
              <div className="login-brand-logo">
                <img src={yiyoTecMark} alt="Yiyo Tec" />
              </div>

              <div className="login-brand-copy">
                <h1>Yiyo Tec</h1>
                <p>Phone &amp; Computer Repair</p>
              </div>
            </div>
          </div>

          <div className="login-hero-copy">
            <h2>Control total del taller en una sola vista</h2>
            <p>
              Gestiona órdenes, clientes, inventario, ventas y reportes desde una interfaz clara,
              moderna y rápida.
            </p>
          </div>

          <div className="login-feature-list">
            <article className="login-feature-card">
              <div className="login-feature-icon login-feature-icon--teal">
                <Wrench size={19} />
              </div>
              <div>
                <strong>Operación centralizada</strong>
                <p>Reparaciones, seguimiento técnico y flujo operativo desde un mismo panel.</p>
              </div>
            </article>

            <article className="login-feature-card">
              <div className="login-feature-icon login-feature-icon--blue">
                <Boxes size={19} />
              </div>
              <div>
                <strong>Inventario conectado</strong>
                <p>Control de productos, variantes, lotes y repuestos con trazabilidad.</p>
              </div>
            </article>

            <article className="login-feature-card">
              <div className="login-feature-icon login-feature-icon--violet">
                <BarChart3 size={19} />
              </div>
              <div>
                <strong>Información útil</strong>
                <p>Métricas, historial comercial y datos clave para tomar mejores decisiones.</p>
              </div>
            </article>
          </div>
        </section>

        <section className="login-panel-wrap" aria-label="Formulario de inicio de sesión">
          <div className="login-panel">
            <div className="login-panel-badge">
              <ShieldCheck size={15} />
              <span>Acceso seguro</span>
            </div>

            <div className="login-panel-header">
              <h3>Iniciar sesión</h3>
              <p>Usa tus credenciales para entrar al sistema con el alcance asignado a tu rol.</p>
            </div>

            {error && (
              <div className="login-alert-error" role="alert">
                {error}
              </div>
            )}

            <form className="login-form" onSubmit={handleLogin}>
              <div className="login-field">
                <label htmlFor="username">Usuario</label>

                <div className="login-input-shell">
                  <span className="login-input-icon">
                    <UserRound size={17} />
                  </span>

                  <input
                    id="username"
                    type="text"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="Ingresa tu usuario"
                    autoComplete="username"
                    autoFocus
                    required
                  />
                </div>
              </div>

              <div className="login-field">
                <label htmlFor="password">Contraseña</label>

                <div className="login-input-shell login-input-shell--password">
                  <span className="login-input-icon">
                    <LockKeyhole size={17} />
                  </span>

                  <input
                    id="password"
                    type={mostrarContrasena ? 'text' : 'password'}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Ingresa tu contraseña"
                    autoComplete="current-password"
                    required
                  />

                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setMostrarContrasena((actual) => !actual)}
                    aria-label={mostrarContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    title={mostrarContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  >
                    {mostrarContrasena ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
              </div>

              <button type="submit" className="login-submit-btn" disabled={loading}>
                <span>{loading ? 'Validando acceso...' : 'Entrar al panel'}</span>
                {!loading && <ArrowRight size={18} />}
              </button>
            </form>

            <div className="login-panel-footer">
              <strong>Acceso restringido</strong>
              <p>Las rutas visibles cambian según el rol y los permisos configurados.</p>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}