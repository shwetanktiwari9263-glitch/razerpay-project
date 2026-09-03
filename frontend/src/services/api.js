// API Configuration
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

// Fetch wrapper with error handling
const apiCall = async (endpoint, options = {}) => {
  const defaultOptions = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...defaultOptions,
      ...options,
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || `API Error: ${response.status}`);
    }

    return await response.json();
  } catch (error) {
    console.error('API Error:', error);
    throw error;
  }
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
