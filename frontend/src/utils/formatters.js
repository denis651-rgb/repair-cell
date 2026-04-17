export const money = new Intl.NumberFormat('es-BO', {
  style: 'currency',
  currency: 'BOB',
  minimumFractionDigits: 2,
});

export function resolvePartUnitPrice(part) {
  const precioUnitario = Number(part?.precioUnitario || 0);
  if (precioUnitario > 0) return precioUnitario;

  const precioVenta = Number(part?.producto?.precioVenta || 0);
  if (precioVenta > 0) return precioVenta;

  return 0;
}

export function resolvePartSubtotal(part) {
  return resolvePartUnitPrice(part) * Number(part?.cantidad || 0);
}

export function resolveVisibleOrderAmount(order) {
  const costoFinal = Number(order?.costoFinal || 0);
  if (costoFinal > 0) return costoFinal;

  const montoPartes = Array.isArray(order?.partes)
    ? order.partes.reduce((sum, part) => sum + resolvePartSubtotal(part), 0)
    : 0;
  if (montoPartes > 0) return montoPartes;

  const costoEstimado = Number(order?.costoEstimado || 0);
  if (costoEstimado > 0) return costoEstimado;

  return 0;
}

export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('es-BO', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-BO');
}
