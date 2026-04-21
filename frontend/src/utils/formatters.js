export const money = new Intl.NumberFormat('es-BO', {
  style: 'currency',
  currency: 'BOB',
  minimumFractionDigits: 2,
});

function pad(value) {
  return String(value).padStart(2, '0');
}

export function toDateInputValue(value = new Date()) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function parseDateValue(value) {
  if (typeof value === 'string') {
    const plainDateMatch = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (plainDateMatch) {
      const [, year, month, day] = plainDateMatch;
      return new Date(Number(year), Number(month) - 1, Number(day));
    }
  }

  return new Date(value);
}

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
  if (!value) return '-';
  const date = parseDateValue(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('es-BO', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

export function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-BO');
}
