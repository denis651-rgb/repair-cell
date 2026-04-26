import { Navigate, useLocation } from 'react-router-dom';
import { getCurrentUser, getDefaultRouteForUser, hasPermission } from '../../utils/permissions';

export default function PermissionRoute({ permission, children }) {
  const location = useLocation();
  const user = getCurrentUser();

  if (!user || !hasPermission(user, permission)) {
    return <Navigate to={getDefaultRouteForUser(user)} replace state={{ from: location.pathname }} />;
  }

  return children;
}
