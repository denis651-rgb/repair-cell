import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, LockKeyhole, ShieldCheck, UserRound, Wrench } from 'lucide-react';
import { api } from '../api/api';
import yiyoTecMark from '../assets/yiyo-tec-mark.svg';
import '../styles/pages/login.css';
import { getCurrentUser, getDefaultRouteForUser } from '../utils/permissions';

export default function LoginPage() {
  const token = localStorage.getItem('token');
  const currentUser = getCurrentUser();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  if (token && currentUser?.rol) {
    return <Navigate to={getDefaultRouteForUser(currentUser)} replace />;
  }

  const handleLogin = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      const data = await api.post('/auth/login', { username, password });
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify({
        username: data.username,
        nombre: data.nombre,
        rol: data.rol,
        permisos: Array.isArray(data.permisos) ? data.permisos : [],
      }));
      navigate(getDefaultRouteForUser({
        username: data.username,
        nombre: data.nombre,
        rol: data.rol,
        permisos: Array.isArray(data.permisos) ? data.permisos : [],
      }));
    } catch (err) {
      setError(err.message || 'No fue posible iniciar sesión.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-page__bg login-page__bg--one" />
      <div className="login-page__bg login-page__bg--two" />

      <section className="login-panel login-panel--brand">
        <div className="login-brand">
          <div className="login-brand__mark-shell">
            <img src={yiyoTecMark} alt="Yiyo Tec" className="login-brand__mark" />
          </div>
          <div>
            <span className="login-brand__eyebrow">Panel Administrativo</span>
            <h1>Yiyo Tec</h1>
            <p>Phone &amp; Computer Repair</p>
          </div>
        </div>

        <div className="login-hero-copy">
          <h2>Control total del taller en una sola vista</h2>
          <p>
            Ingresa al panel principal para gestionar órdenes, clientes, inventario,
            caja diaria y reportes con un flujo más ordenado.
          </p>
        </div>

        <div className="login-feature-list">
          <article className="login-feature-card">
            <div className="login-feature-icon">
              <Wrench size={20} />
            </div>
            <div>
              <strong>Operación centralizada</strong>
              <p>Reparaciones, stock y seguimiento técnico desde el mismo panel.</p>
            </div>
          </article>

          <article className="login-feature-card">
            <div className="login-feature-icon">
              <ShieldCheck size={20} />
            </div>
            <div>
              <strong>Acceso protegido</strong>
              <p>La interfaz muestra solo los modulos permitidos segun los permisos de tu cuenta.</p>
            </div>
          </article>
        </div>
      </section>

      <section className="login-panel login-panel--form">
        <div className="login-form-card">
          <div className="login-form-card__header">
            <span className="login-form-card__badge">Acceso seguro</span>
            <h2>Iniciar sesión</h2>
            <p>Usa tus credenciales para entrar al sistema con el alcance asignado a tu rol.</p>
          </div>

          {error && <div className="login-alert">{error}</div>}

          <form className="login-form" onSubmit={handleLogin}>
            <label className="login-field">
              <span><UserRound size={15} /> Usuario</span>
              <div className="login-input-shell">
                <input
                  type="text"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="Ingresa tu usuario"
                  autoComplete="username"
                  required
                />
              </div>
            </label>

            <label className="login-field">
              <span><LockKeyhole size={15} /> Contraseña</span>
              <div className="login-input-shell">
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  required
                />
              </div>
            </label>

            <button type="submit" className="login-submit" disabled={loading}>
              <span>{loading ? 'Validando acceso...' : 'Entrar al panel'}</span>
              {!loading && <ArrowRight size={18} />}
            </button>
          </form>

          <div className="login-footer-note">
            <strong>Acceso restringido</strong>
            <p>Las rutas y modulos visibles cambian segun el rol y los permisos que entrega el backend.</p>
          </div>
        </div>
      </section>
    </div>
  );
}
