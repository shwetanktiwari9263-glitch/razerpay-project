import React, { useState } from 'react';
import Dashboard from './components/Dashboard';
import PaymentList from './components/PaymentList';
import MlRiskPredictor from './components/MlRiskPredictor';
import './App.css';

function App() {
  const [currentPage, setCurrentPage] = useState('dashboard');

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-content">
          <h1>💳 Payment Recovery Dashboard</h1>
          <p>AI-Powered Razorpay Payment Failure Analysis & Recovery System</p>
        </div>
      </header>

      <nav className="app-navigation">
        <div className="nav-container">
          <button
            className={`nav-link ${currentPage === 'dashboard' ? 'active' : ''}`}
            onClick={() => setCurrentPage('dashboard')}
          >
            📊 Dashboard
          </button>
          <button
            className={`nav-link ${currentPage === 'payments' ? 'active' : ''}`}
            onClick={() => setCurrentPage('payments')}
          >
            💰 Payments
          </button>
          <button
            className={`nav-link ${currentPage === 'predictor' ? 'active' : ''}`}
            onClick={() => setCurrentPage('predictor')}
          >
            ML Predictor
          </button>
        </div>
      </nav>

      <main className="app-main">
        <div className="container">
          {currentPage === 'dashboard' && <Dashboard />}
          {currentPage === 'payments' && <PaymentList />}
          {currentPage === 'predictor' && <MlRiskPredictor />}
        </div>
      </main>

      <footer className="app-footer">
        <p>© 2026 Razorpay Payment Recovery System - Final Year Project</p>
      </footer>
    </div>
  );
}

export default App;
