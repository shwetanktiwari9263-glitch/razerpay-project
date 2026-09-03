import React, { useState, useEffect } from 'react';
import { paymentApi } from '../services/api';
import '../styles/payments.css';

const PaymentList = () => {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState(null);
  const [selectedPayment, setSelectedPayment] = useState(null);

  useEffect(() => {
    fetchPayments();
  }, [page, statusFilter]);

  const fetchPayments = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await paymentApi.getPayments(page, 20, statusFilter);
      setPayments(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      setError(err.message);
      console.error('Failed to fetch payments:', err);
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadgeClass = (status) => {
    const statusMap = {
      CAPTURED: 'status-success',
      AUTHORIZED: 'status-success',
      FAILED: 'status-danger',
      PENDING: 'status-warning',
      REFUNDED: 'status-info',
    };
    return statusMap[status] || 'status-secondary';
  };

  const formatAmount = (amount) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
    }).format(amount);
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="payments-container">
      <div className="payments-header">
        <h2>Payment Events</h2>
        <div className="filter-controls">
          <select
            value={statusFilter || ''}
            onChange={(e) => {
              setStatusFilter(e.target.value || null);
              setPage(0);
            }}
            className="status-filter"
          >
            <option value="">All Status</option>
            <option value="CAPTURED">Captured</option>
            <option value="FAILED">Failed</option>
            <option value="AUTHORIZED">Authorized</option>
            <option value="PENDING">Pending</option>
            <option value="REFUNDED">Refunded</option>
          </select>
        </div>
      </div>

      {loading && <div className="loading-spinner">Loading payments...</div>}
      {error && <div className="error-message">Error: {error}</div>}

      {!loading && !error && (
        <>
          <div className="payments-table-wrapper">
            <table className="payments-table">
              <thead>
                <tr>
                  <th>Payment ID</th>
                  <th>Order ID</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Method</th>
                  <th>Date</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {payments.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="text-center">No payments found</td>
                  </tr>
                ) : (
                  payments.map((payment) => (
                    <tr key={payment.id}>
                      <td>
                        <code className="payment-id">{payment.paymentId}</code>
                      </td>
                      <td>{payment.orderId || 'N/A'}</td>
                      <td className="amount">{formatAmount(payment.amount)}</td>
                      <td>
                        <span className={`badge ${getStatusBadgeClass(payment.status)}`}>
                          {payment.status}
                        </span>
                      </td>
                      <td>{payment.bankOrWallet || payment.gateway || 'N/A'}</td>
                      <td className="date">{formatDate(payment.createdAt)}</td>
                      <td>
                        <button
                          className="btn-view"
                          onClick={() => setSelectedPayment(payment.id)}
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <button
              className="btn-pagination"
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
            >
              Previous
            </button>
            <span className="pagination-info">
              Page {page + 1} of {totalPages}
            </span>
            <button
              className="btn-pagination"
              onClick={() => setPage(page + 1)}
              disabled={page >= totalPages - 1}
            >
              Next
            </button>
          </div>

          {selectedPayment && (
            <PaymentDetailModal
              paymentId={selectedPayment}
              onClose={() => setSelectedPayment(null)}
            />
          )}
        </>
      )}
    </div>
  );
};

const PaymentDetailModal = ({ paymentId, onClose }) => {
  const [payment, setPayment] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [detailError, setDetailError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('info');

  useEffect(() => {
    fetchPaymentDetails();
  }, [paymentId]);

  const fetchPaymentDetails = async () => {
    try {
      setLoading(true);
      setDetailError(null);
      const [paymentResult, predictionResult, analysisResult] = await Promise.allSettled([
        paymentApi.getPaymentDetail(paymentId), paymentApi.getPaymentPrediction(paymentId), paymentApi.getPaymentAiAnalysis(paymentId),
      ]);
      if (paymentResult.status === 'rejected') throw paymentResult.reason;
      setPayment(paymentResult.value);
      setPrediction(predictionResult.status === 'fulfilled' ? predictionResult.value : null);
      setAnalysis(analysisResult.status === 'fulfilled' ? analysisResult.value : null);
      if (predictionResult.status === 'rejected' || analysisResult.status === 'rejected') {
        setDetailError('ML prediction and AI recovery analysis are available only for processed failed payments.');
      }
    } catch (err) {
      console.error('Failed to fetch payment details:', err);
      setDetailError(err.message || 'Payment details could not be loaded.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal-content" onClick={(e) => e.stopPropagation()}>
          <div className="loading-spinner">Loading...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <button className="modal-close" onClick={onClose}>&times;</button>
        
        <div className="modal-header">
          <h3>Payment Details</h3>
        </div>

        <div className="modal-tabs">
          <button
            className={`tab ${activeTab === 'info' ? 'active' : ''}`}
            onClick={() => setActiveTab('info')}
          >
            Info
          </button>
          <button
            className={`tab ${activeTab === 'prediction' ? 'active' : ''}`}
            onClick={() => setActiveTab('prediction')}
          >
            ML Prediction
          </button>
          <button
            className={`tab ${activeTab === 'analysis' ? 'active' : ''}`}
            onClick={() => setActiveTab('analysis')}
          >
            AI Analysis
          </button>
        </div>

        <div className="modal-body">
          {detailError && <div className="error-message">{detailError}</div>}
          {activeTab === 'info' && payment && (
            <div className="payment-info-panel">
              <div className="info-row">
                <label>Payment ID:</label>
                <code>{payment.paymentId}</code>
              </div>
              <div className="info-row">
                <label>Order ID:</label>
                <span>{payment.orderId || 'N/A'}</span>
              </div>
              <div className="info-row">
                <label>Amount:</label>
                <span>₹{payment.amount}</span>
              </div>
              <div className="info-row">
                <label>Status:</label>
                <span className="badge" style={{ backgroundColor: getStatusColor(payment.status) }}>
                  {payment.status}
                </span>
              </div>
              <div className="info-row">
                <label>Payment Method:</label>
                <span>{payment.bankOrWallet || payment.gateway || 'N/A'}</span>
              </div>
              {payment.errorCode && (
                <div className="info-row">
                  <label>Error Code:</label>
                  <span>{payment.errorCode}</span>
                </div>
              )}
              {payment.errorDescription && (
                <div className="info-row">
                  <label>Error Description:</label>
                  <span>{payment.errorDescription}</span>
                </div>
              )}
              <div className="info-row">
                <label>Created At:</label>
                <span>{new Date(payment.createdAt).toLocaleString('en-IN')}</span>
              </div>
            </div>
          )}

          {activeTab === 'prediction' && prediction && (
            <div className="prediction-panel">
              <h4>ML PREDICTION</h4>
              <div className="risk-card">
                <h4>Risk Level</h4>
                <div className={`risk-badge ${prediction.riskLevel.toLowerCase()}`}>
                  {prediction.riskLevel}
                </div>
              </div>
              <div className="prediction-metrics">
                <div className="metric">
                  <label>Success Probability</label>
                  <div className="progress-bar">
                    <div
                      className="progress"
                      style={{
                        width: `${prediction.successProbability * 100}%`,
                        backgroundColor: '#28a745',
                      }}
                    >
                      {(prediction.successProbability * 100).toFixed(1)}%
                    </div>
                  </div>
                </div>
                <div className="metric">
                  <label>Failure Probability</label>
                  <div className="progress-bar">
                    <div
                      className="progress"
                      style={{
                        width: `${prediction.failureProbability * 100}%`,
                        backgroundColor: '#dc3545',
                      }}
                    >
                      {(prediction.failureProbability * 100).toFixed(1)}%
                    </div>
                  </div>
                </div>
              </div>
              <div className="info-row">
                <label>Model:</label>
                <span>{prediction.modelName}</span>
              </div>
              <div className="info-row">
                <label>Confidence:</label>
                <span>{(prediction.modelConfidence * 100).toFixed(1)}%</span>
              </div>
            </div>
          )}
          {activeTab === 'prediction' && !prediction && <p>No ML prediction is available for this payment.</p>}

          {activeTab === 'analysis' && analysis && (
            <div className="analysis-panel">
              <h4>AI RECOVERY ANALYSIS</h4>
              <div className="analysis-section">
                <h4>Likely Cause</h4>
                <p>{analysis.cause || 'No cause analysis available'}</p>
              </div>
              <div className="analysis-section">
                <h4>Explanation</h4>
                <p>{analysis.explanation || 'No explanation available'}</p>
              </div>
              <div className="analysis-section">
                <h4>What can be done</h4>
                <ol>
                  {(analysis.recommendedActions || analysis.recoverySteps || [])
                    .map((step, idx) => (
                      <li key={idx}>{step}</li>
                    ))}
                </ol>
              </div>
              <div className="analysis-section">
                <h4>Recommended next action</h4>
                <p>{analysis.recommendedAction || 'No action recommended'}</p>
              </div>
              {analysis.alternatives?.length > 0 && (
                <div className="analysis-section">
                  <h4>Alternative payment / recovery options</h4>
                  <p>{analysis.alternatives.join(', ')}</p>
                </div>
              )}
              <div className="info-row">
                <label>Priority:</label>
                <span>{analysis.priority}</span>
              </div>
              <div className="info-row">
                <label>AI Confidence:</label>
                <span>{(analysis.confidenceScore * 100).toFixed(1)}%</span>
              </div>
            </div>
          )}
          {activeTab === 'analysis' && !analysis && <p>No AI recovery analysis is available for this payment.</p>}
        </div>
      </div>
    </div>
  );
};

const getStatusColor = (status) => {
  const colors = {
    CAPTURED: '#28a745',
    AUTHORIZED: '#17a2b8',
    FAILED: '#dc3545',
    PENDING: '#ffc107',
    REFUNDED: '#6c757d',
  };
  return colors[status] || '#6c757d';
};

export default PaymentList;
