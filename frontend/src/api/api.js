const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');

function buildUrl(path, query) {
  const url = new URL(`${API_URL}${path}`);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      url.searchParams.set(key, String(value));
    });
  }
  return url.toString();
}

async function parseResponse(response) {
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

function extractErrorMessage(payload) {
  if (!payload) return 'Ocurrió un error inesperado';
  if (typeof payload === 'string') return payload;
  return payload.message || payload.error || payload.mensaje || 'Ocurrió un error inesperado';
}

async function request(path, options = {}) {
  const token = localStorage.getItem('token');
  const { query, body, headers, ...rest } = options;
  const response = await fetch(buildUrl(path, query), {
    ...rest,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(headers || {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  if (response.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  if (!response.ok) {
    let payload = null;
    try {
      payload = await parseResponse(response);
    } catch {
      payload = null;
    }
    throw new Error(extractErrorMessage(payload));
  }

  return parseResponse(response);
}

export const api = {
  get: (path, query) => request(path, { method: 'GET', query }),
  post: (path, body, query) => request(path, { method: 'POST', body, query }),
  put: (path, body, query) => request(path, { method: 'PUT', body, query }),
  patch: (path, body, query) => request(path, { method: 'PATCH', body, query }),
  delete: (path, query) => request(path, { method: 'DELETE', query }),
  raw: request,
};
