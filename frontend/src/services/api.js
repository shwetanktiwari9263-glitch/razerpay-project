// API Configuration
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

// Fetch wrapper with timeout, clear network errors, and retries for safe reads.
// Payment-changing requests must not be retried automatically because a retry
// could create a duplicate action.
const apiCall = async (endpoint, options = {}, retries = 2) => {
  const url = `${API_BASE_URL}${endpoint}`;
  const method = (options.method || 'GET').toUpperCase();

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10_000);

    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          ...options.headers,
        },
        signal: controller.signal,
      });

      const contentType = response.headers.get('content-type') || '';
      const body = contentType.includes('application/json')
        ? await response.json()
        : await response.text();

      if (!response.ok) {
        const message = typeof body === 'object' && body !== null
          ? body.message
          : body;
        throw new Error(message || `API Error: ${response.status}`);
      }

      return body;
    } catch (error) {
      const networkFailure = error instanceof TypeError || error.name === 'AbortError';
      const canRetry = method === 'GET' && networkFailure && attempt < retries;

      if (canRetry) {
        await delay(500 * (2 ** attempt));
        continue;
      }

      if (error.name === 'AbortError') {
        throw new Error('The API request timed out. Check whether the backend is running and reachable.');
      }

      if (error instanceof TypeError) {
        throw new Error(`Cannot reach the API at ${API_BASE_URL}. Start the backend and check its port and CORS configuration.`);
      }

      console.error('API Error:', error);
      throw error;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  throw new Error('The API request could not be completed.');
};

// Payment API endpoints
export const paymentApi = {
  // Get all payments with pagination
  getPayments: (page = 0, size = 20, status = null) => {
    let url = `/payments?page=${page}&size=${size}`;
    if (status) url += `&status=${status}`;
    return apiCall(url);
  },

  // Get payment detail by ID
  getPaymentDetail: (id) => apiCall(`/payments/${id}`),

  // Get ML prediction for a payment
  getPaymentPrediction: (id) => apiCall(`/payments/${id}/prediction`),

  // Get AI analysis for a payment
  getPaymentAiAnalysis: (id) => apiCall(`/payments/${id}/ai-analysis`),
};

// Dashboard API endpoints
export const dashboardApi = {
  // Get dashboard summary with optional period
  getSummary: (period = 'MONTH') => apiCall(`/dashboard/summary?period=${period}`),

  // Get failure analysis
  getFailureAnalysis: (period = 'MONTH') => apiCall(`/dashboard/failure-analysis?period=${period}`),

  getAiRecoveryAnalyses: () => apiCall('/dashboard/ai-recovery-analyses'),
};

export const mlPredictionApi = {
  predict: (features) => apiCall('/prediction/failure-risk', {
    method: 'POST',
    body: JSON.stringify(features),
  }),
};

export default { paymentApi, dashboardApi, mlPredictionApi };
