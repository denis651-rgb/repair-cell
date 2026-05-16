import { Routes, Route, Navigate } from 'react-router-dom';
import PermissionRoute from './components/auth/PermissionRoute';
import AppLayout from './layout/AppLayout';
import DashboardPage from './pages/DashboardPage';
import CustomersPage from './pages/CustomersPage';
import DevicesPage from './pages/DevicesPage';
import RepairOrdersPage from './pages/RepairOrdersPage';
import TicketPage from './pages/TicketPage';
import InventoryPage from './pages/InventoryPage';
import ProveedoresPage from './pages/ProveedoresPage';
import ComprasPage from './pages/ComprasPage';
import VentasPage from './pages/VentasPage';
import CuentasPorCobrarPage from './pages/CuentasPorCobrarPage';
import AccountingPage from './pages/AccountingPage';
import ReportesPage from './pages/ReportesPage';
import BackupsPage from './pages/BackupsPage';
import AccessDeniedPage from './pages/AccessDeniedPage';
import LoginPage from './pages/LoginPage';
import { clearStoredSession, getCurrentUser, getDefaultRouteForUser } from './utils/permissions';

function ProtectedRoute({ children }) {
  const token = localStorage.getItem('token');
  const user = getCurrentUser();

  if (!token || !user?.username || !user?.rol) {
    clearStoredSession();
    return <Navigate to="/login" replace />;
  }

  return children;
}

function HomeRedirect() {
  const user = getCurrentUser();
  return <Navigate to={getDefaultRouteForUser(user)} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route path="/" element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
        <Route index element={<HomeRedirect />} />

        <Route
          path="dashboard"
          element={<PermissionRoute permission="DASHBOARD_VIEW"><DashboardPage /></PermissionRoute>}
        />

        <Route
          path="clientes"
          element={<PermissionRoute permission="CLIENTES_VIEW"><CustomersPage /></PermissionRoute>}
        />

        <Route
          path="dispositivos"
          element={<PermissionRoute permission="DISPOSITIVOS_VIEW"><DevicesPage /></PermissionRoute>}
        />

        <Route
          path="reparaciones"
          element={<PermissionRoute permission="REPARACIONES_VIEW"><RepairOrdersPage /></PermissionRoute>}
        />

        <Route
          path="tickets/:id"
          element={<PermissionRoute permission="REPARACIONES_VIEW"><TicketPage /></PermissionRoute>}
        />

        <Route
          path="inventario"
          element={<PermissionRoute permission="INVENTARIO_VIEW"><InventoryPage /></PermissionRoute>}
        />

        <Route
          path="proveedores"
          element={<PermissionRoute permission="PROVEEDORES_VIEW"><ProveedoresPage /></PermissionRoute>}
        />

        <Route
          path="compras"
          element={<PermissionRoute permission="COMPRAS_VIEW"><ComprasPage /></PermissionRoute>}
        />

        <Route
          path="ventas"
          element={<PermissionRoute permission="VENTAS_VIEW"><VentasPage /></PermissionRoute>}
        />

        <Route
          path="cuentas-por-cobrar"
          element={<PermissionRoute permission="CUENTAS_POR_COBRAR_VIEW"><CuentasPorCobrarPage /></PermissionRoute>}
        />

        <Route
          path="contabilidad"
          element={<PermissionRoute permission="CONTABILIDAD_VIEW"><AccountingPage /></PermissionRoute>}
        />

        <Route
          path="reportes"
          element={<PermissionRoute permission="REPORTES_VIEW"><ReportesPage /></PermissionRoute>}
        />

        <Route
          path="respaldos"
          element={<PermissionRoute permission="BACKUPS_VIEW"><BackupsPage /></PermissionRoute>}
        />

        <Route path="sin-permiso" element={<AccessDeniedPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}