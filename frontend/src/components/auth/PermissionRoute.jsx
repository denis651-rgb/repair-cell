import { Navigate, useLocation } from 'react-router-dom';
import { getCurrentUser, getDefaultRouteForUser, hasPermission } from '../../utils/permissions';

export default function PermissionRoute({ permission, children }) {
  const location = useLocation();
  const user = getCurrentUser();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!hasPermission(user, permission)) {
    const defaultRoute = getDefaultRouteForUser(user);
    const targetRoute = defaultRoute && defaultRoute !== location.pathname ? defaultRoute : '/sin-permiso';

    return <Navigate to={targetRoute} replace state={{ from: location.pathname }} />;
  }

  return children;
}