import { ShieldAlert } from 'lucide-react';
import PageHeader from '../components/PageHeader';
import { getCurrentUser } from '../utils/permissions';

export default function AccessDeniedPage() {
  const user = getCurrentUser();

  return (
    <div className="page-stack">
      <PageHeader
        title="Sin permisos disponibles"
        subtitle="Tu usuario no tiene permisos asignados para acceder a los módulos del sistema."
      />

      <section className="card empty-state">
        <div className="empty-state-icon">
          <ShieldAlert size={34} />
        </div>

        <h3>Acceso restringido</h3>

        <p>
          Iniciaste sesión correctamente, pero tu rol actual no tiene permisos activos.
          Solicita a un usuario administrador que revise la configuración de permisos.
        </p>

        <div className="sale-line-muted">
          Usuario actual: {user?.nombre || user?.username || 'No disponible'}
        </div>
      </section>
    </div>
  );
}