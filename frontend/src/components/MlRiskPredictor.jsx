import React, { useState } from 'react';
import { mlPredictionApi } from '../services/api';
import '../styles/ml-predictor.css';

const initialForm = {
  transactionId: `ui-${Date.now()}`, amount: 5000, transactionType: 'Merchant', merchantCategory: 'Retail',
  hourOfDay: new Date().getHours(), weekend: [0, 6].includes(new Date().getDay()), senderBank: 'HDFC',
  receiverBank: 'SBI', senderState: 'Delhi', senderAgeGroup: '26-35', receiverAgeGroup: '26-35',
  deviceType: 'Android', networkType: '4G', fraudFlagSet: false,
};

const options = {
  transactionType: ['Merchant', 'P2P'], merchantCategory: ['Retail', 'Entertainment', 'Food', 'Travel', 'Gambling'],
  senderBank: ['HDFC', 'SBI', 'ICICI', 'Axis', 'PNB'], receiverBank: ['SBI', 'HDFC', 'ICICI', 'Axis', 'PNB'],
  senderState: ['Delhi', 'Mumbai', 'Karnataka', 'Tamil Nadu', 'Assam'], senderAgeGroup: ['18-25', '26-35', '35-45', '46-65', '65+'],
  receiverAgeGroup: ['18-25', '26-35', '35-45', '46-65', '65+'], deviceType: ['Android', 'iPhone', 'Web'], networkType: ['5G', '4G', '3G', '2G', 'WiFi'],
};

const fields = [
  ['transactionId', 'Transaction ID'], ['amount', 'Amount (INR)'], ['transactionType', 'Transaction type'],
  ['merchantCategory', 'Merchant category'], ['hourOfDay', 'Hour of day'], ['networkType', 'Network type'],
  ['senderBank', 'Sender bank'], ['receiverBank', 'Receiver bank'], ['senderState', 'Sender state'],
  ['senderAgeGroup', 'Sender age group'], ['receiverAgeGroup', 'Receiver age group'], ['deviceType', 'Device'],
];

export default function MlRiskPredictor() {
  const [form, setForm] = useState(initialForm);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const update = ({ target }) => setForm((current) => ({ ...current, [target.name]: target.type === 'checkbox' ? target.checked : target.value }));
  const submit = async (event) => {
    event.preventDefault(); setError(null); setLoading(true);
    try { setResult(await mlPredictionApi.predict({ ...form, amount: Number(form.amount), hourOfDay: Number(form.hourOfDay) })); }
    catch (requestError) { setResult(null); setError(requestError.message || 'The ML prediction service is unavailable.'); }
    finally { setLoading(false); }
  };
  return <section className="ml-predictor">
    <header><p className="ml-kicker">ML prediction engine</p><h2>Predict payment risk</h2><p>Estimate payment success or failure risk before a payment attempt. This page does not generate AI recovery advice.</p></header>
    <form className="ml-form" onSubmit={submit}>
      {fields.map(([name, label]) => <label className="ml-field" key={name}><span>{label}</span>{options[name]
        ? <select name={name} value={form[name]} onChange={update}>{options[name].map((value) => <option key={value}>{value}</option>)}</select>
        : <input name={name} type={name === 'amount' || name === 'hourOfDay' ? 'number' : 'text'} min={name === 'hourOfDay' ? 0 : undefined} max={name === 'hourOfDay' ? 23 : undefined} value={form[name]} onChange={update} required />}</label>)}
      <label className="ml-check"><input name="weekend" type="checkbox" checked={form.weekend} onChange={update} /> Weekend transaction</label>
      <label className="ml-check"><input name="fraudFlagSet" type="checkbox" checked={form.fraudFlagSet} onChange={update} /> Fraud flag set</label>
      <button className="ml-submit" disabled={loading}>{loading ? 'Running model...' : 'Run ML prediction'}</button>
    </form>
    {error && <p className="ml-error">{error}</p>}
    {result && <div className="ml-result" aria-live="polite">
      <Metric label="Success probability" value={`${(result.successProbability * 100).toFixed(1)}%`} />
      <Metric label="Failure probability" value={`${(result.failureProbability * 100).toFixed(1)}%`} />
      <Metric label="Risk level" value={result.riskLevel} className={`ml-risk ${result.riskLevel.toLowerCase()}`} />
      <p>Model: {result.predictorModel}</p>
    </div>}
  </section>;
}

function Metric({ label, value, className = '' }) { return <div><span>{label}</span><strong className={className}>{value}</strong></div>; }
