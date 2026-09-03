import React, { useState, useEffect } from 'react';
import { dashboardApi } from '../services/api';
import '../styles/dashboard.css';

const Dashboard = () => {
  const [summary, setSummary] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [recoveryAnalyses, setRecoveryAnalyses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [period, setPeriod] = useState('MONTH');

  useEffect(() => {
    fetchDashboardData();
  }, [period]);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [summaryData, analysisData, recoveryData] = await Promise.all([
        dashboardApi.getSummary(period),
        dashboardApi.getFailureAnalysis(period),
        dashboardApi.getAiRecoveryAnalyses(),
      ]);
      setSummary(summaryData);
      setAnalysis(analysisData);
      setRecoveryAnalyses(recoveryData);
    } catch (err) {
      setError(err.message);
      console.error('Failed to fetch dashboard:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="dashboard loading">Loading dashboard...</div>;
  }

  if (error) {
    return (
      <div className="dashboard error">
        <p>Error: {error}</p>
        <button onClick={fetchDashboardData}>Retry</button>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>Payment Recovery Dashboard</h2>
        <div className="period-selector">
          <select value={period} onChange={(e) => setPeriod(e.target.value)}>
            <option value="TODAY">Today</option>
            <option value="WEEK">Last 7 Days</option>
            <option value="MONTH">Last 30 Days</option>
            <option value="ALL_TIME">All Time</option>
          </select>
        </div>
      </div>

      {summary && (
        <>
          {/* Key Metrics */}
          <div className="metrics-grid">
            <MetricCard
              title="Total Payments"
              value={summary.totalPayments}
              subtext={`Success Rate: ${summary.successRate?.toFixed(2)}%`}
              color="#3498db"
            />
            <MetricCard
              title="Successful Payments"
              value={summary.successfulPayments}
              subtext={`${((summary.successfulPayments / summary.totalPayments) * 100 || 0).toFixed(1)}%`}
              color="#27ae60"
            />
            <MetricCard
              title="Failed Payments"
              value={summary.failedPayments}
              subtext={`${summary.failureRate?.toFixed(2)}%`}
              color="#e74c3c"
            />
            <MetricCard
              title="Recovered Payments"
              value={summary.recoveredPayments}
              subtext={`Recovery Rate: ${summary.recoveryRatePercent?.toFixed(2)}%`}
              color="#f39c12"
            />
          </div>

          {/* Recovery Analytics */}
          <div className="analytics-section">
            <h3>Recovery Analytics</h3>
            <div className="analytics-grid">
              <Card>
                <h4>Total Recovered Amount</h4>
                <div className="large-value">
                  ₹{summary.totalRecoveredAmount?.toLocaleString('en-IN')}
                </div>
              </Card>
              <Card>
                <h4>Avg Recovery Time</h4>
                <div className="large-value">
                  {summary.averageRecoveryTime?.toFixed(1)} mins
                </div>
              </Card>
              <Card>
                <h4>High Risk Payments</h4>
                <div className="large-value" style={{ color: '#e74c3c' }}>
                  {summary.highRiskPayments}
                </div>
              </Card>
            </div>
          </div>

          {/* Risk Distribution Chart */}
          {summary && (
            <div className="chart-section">
              <h3>Risk Distribution</h3>
              <div className="risk-distribution">
                <RiskChart
                  lowRisk={summary.lowRiskCount || 0}
                  mediumRisk={summary.mediumRiskCount || 0}
                  highRisk={summary.highRiskCount || 0}
                />
              </div>
            </div>
          )}

          {/* Top Failing Payment Method */}
          {summary?.topFailingMethod && (
            <div className="chart-section">
              <h3>Top Issue</h3>
              <Card>
                <p>
                  Most Common Failing Payment Method: <strong>{summary.topFailingMethod}</strong>
                </p>
                <p>
                  Failure Rate: <strong>{summary.topMethodFailureRate?.toFixed(2)}%</strong>
                </p>
              </Card>
            </div>
          )}

          {/* Error Code Distribution */}
          {analysis && Object.keys(analysis.errorCodeDistribution || {}).length > 0 && (
            <div className="chart-section">
              <h3>Error Code Distribution</h3>
              <div className="error-codes">
                {Object.entries(analysis.errorCodeDistribution || {})
                  .sort(([, a], [, b]) => b - a)
                  .slice(0, 5)
                  .map(([code, count]) => (
                    <div key={code} className="error-code-bar">
                      <div className="code-label">{code}</div>
                      <div className="code-bar">
                        <div
                          className="code-fill"
                          style={{
                            width: `${(count / Math.max(...Object.values(analysis.errorCodeDistribution))) * 100}%`,
                          }}
                        >
                          {count}
                        </div>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          )}

          {/* Payment Method Failure Rate */}
          {analysis &&
            Object.keys(analysis.paymentMethodFailureRate || {}).length > 0 && (
              <div className="chart-section">
                <h3>Payment Method Failure Rate</h3>
                <div className="method-failure-rates">
                  {Object.entries(analysis.paymentMethodFailureRate || {})
                    .sort(([, a], [, b]) => b - a)
                    .map(([method, rate]) => (
                      <div key={method} className="method-rate">
                        <span className="method-name">{method}</span>
                        <div className="rate-bar">
                          <div
                            className="rate-fill"
                            style={{
                              width: `${rate}%`,
                              backgroundColor: rate > 50 ? '#e74c3c' : '#f39c12',
                            }}
                          >
                            {rate?.toFixed(1)}%
                          </div>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )}

          {/* Recent Failures */}
          {analysis?.recentFailures && analysis.recentFailures.length > 0 && (
            <div className="chart-section">
              <h3>Recent Failures</h3>
              <div className="recent-failures">
                <table>
                  <thead>
                    <tr>
                      <th>Payment ID</th>
                      <th>Amount</th>
                      <th>Error Code</th>
                      <th>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {analysis.recentFailures.slice(0, 5).map((payment) => (
                      <tr key={payment.id}>
                        <td>
                          <code>{payment.paymentId}</code>
                        </td>
                        <td>₹{payment.amount}</td>
                        <td>{payment.errorCode || 'N/A'}</td>
                        <td>{new Date(payment.createdAt).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <div className="chart-section groq-recovery-section">
            <h3>AI Recovery Dashboard</h3>
            <p className="groq-caption">Groq uses Razorpay error details and ML prediction context to recommend the next recovery action. It does not predict payment status.</p>
            {recoveryAnalyses.length === 0 ? (
              <Card><p>No payment-failure recovery analyses are available yet.</p></Card>
            ) : (
              <div className="groq-analysis-grid">
                {recoveryAnalyses.map((item) => (
                  <article className="groq-analysis-card" key={`${item.paymentId}-${item.createdAt}`}>
                    <div className="groq-analysis-meta"><code>{item.paymentId}</code><span className={`groq-priority ${item.priority.toLowerCase()}`}>{item.priority}</span></div>
                    <h4>{item.cause}</h4>
                    <p>{item.explanation}</p>
                    <strong>Next action: {item.recommendedAction}</strong>
                    {item.recoverySteps?.length > 0 && <ol>{item.recoverySteps.map((step, index) => <li key={index}>{step}</li>)}</ol>}
                    {item.alternatives?.length > 0 && <p className="groq-alternatives">Alternatives: {item.alternatives.join(', ')}</p>}
                    <small>Provider: {item.aiModel}</small>
                  </article>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};

const MetricCard = ({ title, value, subtext, color }) => (
  <div className="metric-card" style={{ borderLeft: `4px solid ${color}` }}>
    <h3>{title}</h3>
    <div className="metric-value">{value}</div>
    {subtext && <p className="metric-subtext">{subtext}</p>}
  </div>
);

const Card = ({ children }) => <div className="card">{children}</div>;

const RiskChart = ({ lowRisk, mediumRisk, highRisk }) => {
  const total = lowRisk + mediumRisk + highRisk || 1;
  return (
    <div className="risk-chart">
      <div className="risk-item">
        <div className="risk-bar" style={{ width: `${(lowRisk / total) * 100}%`, backgroundColor: '#27ae60' }}>
          Low
        </div>
        <div className="risk-label">Low: {lowRisk}</div>
      </div>
      <div className="risk-item">
        <div className="risk-bar" style={{ width: `${(mediumRisk / total) * 100}%`, backgroundColor: '#f39c12' }}>
          Medium
        </div>
        <div className="risk-label">Medium: {mediumRisk}</div>
      </div>
      <div className="risk-item">
        <div className="risk-bar" style={{ width: `${(highRisk / total) * 100}%`, backgroundColor: '#e74c3c' }}>
          High
        </div>
        <div className="risk-label">High: {highRisk}</div>
      </div>
    </div>
  );
};

export default Dashboard;
