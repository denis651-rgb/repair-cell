import { Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './layout/AppLayout';
import DashboardPage from './pages/DashboardPage';
import CustomersPage from './pages/CustomersPage';
import DevicesPage from './pages/DevicesPage';
import RepairOrdersPage from './pages/RepairOrdersPage';
import TicketPage from './pages/TicketPage';
import InventoryPage from './pages/InventoryPage';
import AccountingPage from './pages/AccountingPage';
import ReportesPage from './pages/ReportesPage';
import BackupsPage from './pages/BackupsPage';

import LoginPage from './pages/LoginPage';

function ProtectedRoute({ children }) {
  const token = localStorage.getItem('token');
  let user = {};
  try {
    user = JSON.parse(localStorage.getItem('user') || '{}');
  } catch {
    user = {};
  }

  if (!token || user?.rol !== 'ADMIN') {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    return <Navigate to="/login" replace />;
  }

  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="clientes" element={<CustomersPage />} />
        <Route path="dispositivos" element={<DevicesPage />} />
        <Route path="reparaciones" element={<RepairOrdersPage />} />
        <Route path="tickets/:id" element={<TicketPage />} />
        <Route path="inventario" element={<InventoryPage />} />
        <Route path="contabilidad" element={<AccountingPage />} />
        <Route path="reportes" element={<ReportesPage />} />
        <Route path="respaldos" element={<BackupsPage />} />
      </Route>
    </Routes>
  );
}
