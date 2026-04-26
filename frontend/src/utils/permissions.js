const FALLBACK_ROUTE = '/reparaciones';

const PERMISSION_ROUTE_PRIORITY = [
  { permission: 'DASHBOARD_VIEW', path: '/dashboard' },
  { permission: 'REPARACIONES_VIEW', path: '/reparaciones' },
  { permission: 'CLIENTES_VIEW', path: '/clientes' },
  { permission: 'DISPOSITIVOS_VIEW', path: '/dispositivos' },
  { permission: 'INVENTARIO_VIEW', path: '/inventario' },
  { permission: 'PROVEEDORES_VIEW', path: '/proveedores' },
  { permission: 'COMPRAS_VIEW', path: '/compras' },
  { permission: 'VENTAS_VIEW', path: '/ventas' },
  { permission: 'CUENTAS_POR_COBRAR_VIEW', path: '/cuentas-por-cobrar' },
  { permission: 'CONTABILIDAD_VIEW', path: '/contabilidad' },
  { permission: 'REPORTES_VIEW', path: '/reportes' },
  { permission: 'BACKUPS_VIEW', path: '/respaldos' },
];

export function getCurrentUser() {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;

    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }

    return {
      ...parsed,
      permisos: Array.isArray(parsed.permisos) ? parsed.permisos : [],
    };
  } catch {
    return null;
  }
}

export function hasPermission(user, permission) {
  if (!user || !permission) return false;
  return Array.isArray(user.permisos) && user.permisos.includes(permission);
}

export function hasAnyPermission(user, permissions = []) {
  return permissions.some((permission) => hasPermission(user, permission));
}

export function getDefaultRouteForUser(user) {
  if (!user) return FALLBACK_ROUTE;

  const match = PERMISSION_ROUTE_PRIORITY.find((item) => hasPermission(user, item.permission));
  return match?.path || FALLBACK_ROUTE;
}

export function clearStoredSession() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
}
