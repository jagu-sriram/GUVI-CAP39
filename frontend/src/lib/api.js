const BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const TIMEOUT = Number(import.meta.env.VITE_API_TIMEOUT_MS || 30000);

export class ApiError extends Error {
  constructor(message, status = 0, details = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

export async function request(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT);

  try {
    const headers = {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers
    };

    const response = await fetch(`${BASE}${path}`, {
      ...options,
      headers,
      signal: controller.signal
    });

    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }

    if (!response.ok) {
      const message =
        data?.message ||
        data?.error ||
        data?.detail ||
        (typeof data === 'string' ? data : `Request failed (${response.status})`);
      throw new ApiError(message, response.status, data);
    }

    return data;
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new ApiError('Request timed out. The server may still be processing. Refresh status before retrying.');
    }
    if (error instanceof ApiError) throw error;
    throw new ApiError('Cannot reach CAP-39 backend. Check Spring Boot and the configured API URL.');
  } finally {
    clearTimeout(timeout);
  }
}

function queryString(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      search.set(key, String(value));
    }
  });
  const encoded = search.toString();
  return encoded ? `?${encoded}` : '';
}

export const api = {
  health: () => request('/api/health/snowflake'),
  s3Health: () => request('/api/health/s3', { method: 'POST' }),

  uploads: () => request('/api/uploads'),
  upload: (file, uploadedBy) => {
    const body = new FormData();
    body.append('file', file);
    body.append('uploadedBy', uploadedBy);
    return request('/api/uploads', { method: 'POST', body });
  },
  uploadById: id => request(`/api/uploads/${encodeURIComponent(id)}`),

  startPipeline: id => request(`/api/uploads/${encodeURIComponent(id)}/pipeline`, { method: 'POST' }),
  pipelineStatus: id => request(`/api/uploads/${encodeURIComponent(id)}/pipeline`),
  retryPipeline: id => request(`/api/uploads/${encodeURIComponent(id)}/pipeline/retry`, { method: 'POST' }),

  batches: () => request('/api/batches'),
  defects: () => request('/api/defects'),
  registerBatch: payload => request('/api/batches', { method: 'POST', body: JSON.stringify(payload) }),
  registerDefect: payload => request('/api/defects', { method: 'POST', body: JSON.stringify(payload) }),

  analytics: (name, params = {}) => request(`/api/analytics/${name}${queryString(params)}`)
};

export const DATASETS = [
  'batch_relationships',
  'defects',
  'machines',
  'material_lots',
  'operators',
  'process_parameters',
  'production_batches',
  'production_events',
  'production_lines',
  'products',
  'rework_records',
  'shift_schedule',
  'suppliers'
];
