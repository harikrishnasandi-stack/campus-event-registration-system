import { useState, useEffect, useCallback } from 'react';
import type { User, Page } from '../types';
import {
  apiGetAdminPayments,
  apiGetAdminPaymentCounts,
  apiApprovePayment,
  apiRejectPayment,
  getPaymentProofUrl,
  type ApiPaymentSubmission,
} from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function AdminPayments({ user: _user, navigate: _navigate }: Props) {
  const [payments, setPayments]       = useState<ApiPaymentSubmission[]>([]);
  const [counts, setCounts]           = useState<Record<string, number>>({ ALL: 0, PENDING: 0, APPROVED: 0, REJECTED: 0 });
  const [filter, setFilter]           = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');
  const [actionMsg, setActionMsg]     = useState('');

  // Modals
  const [selectedProof, setSelectedProof] = useState<ApiPaymentSubmission | null>(null);
  const [rejectingItem, setRejectingItem] = useState<ApiPaymentSubmission | null>(null);
  const [rejectReason, setRejectReason]   = useState('');
  const [processingId, setProcessingId]   = useState<number | null>(null);

  const loadData = useCallback(() => {
    setLoading(true);
    setError('');
    Promise.all([
      apiGetAdminPayments(filter),
      apiGetAdminPaymentCounts(),
    ])
      .then(([items, cnts]) => {
        setPayments(items);
        setCounts(cnts);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [filter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleApprove = async (item: ApiPaymentSubmission) => {
    if (!window.confirm(`Are you sure you want to approve payment of ₹${item.amount} from ${item.studentName}? This will confirm their registration.`)) {
      return;
    }
    setProcessingId(item.id);
    setActionMsg('');
    try {
      const res = await apiApprovePayment(item.id);
      setActionMsg(`✅ ${res.message}`);
      loadData();
      setTimeout(() => setActionMsg(''), 5000);
    } catch (err) {
      setActionMsg(`⚠️ ${err instanceof Error ? err.message : 'Approval failed'}`);
    } finally {
      setProcessingId(null);
    }
  };

  const openRejectModal = (item: ApiPaymentSubmission) => {
    setRejectingItem(item);
    setRejectReason('');
  };

  const handleConfirmReject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rejectingItem) return;
    if (!rejectReason.trim()) {
      alert('Please provide a reason for rejecting this payment.');
      return;
    }

    setProcessingId(rejectingItem.id);
    setActionMsg('');
    try {
      const res = await apiRejectPayment(rejectingItem.id, rejectReason.trim());
      setActionMsg(`✅ ${res.message}`);
      setRejectingItem(null);
      loadData();
      setTimeout(() => setActionMsg(''), 5000);
    } catch (err) {
      setActionMsg(`⚠️ ${err instanceof Error ? err.message : 'Rejection failed'}`);
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <h1 className="page-title">💳 UPI Payment Verification</h1>
            <p className="page-subtitle">Verify student fee payments and approve event registrations</p>
          </div>
          <button className="btn btn-outline" onClick={loadData}>🔄 Refresh</button>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {actionMsg && (
          <div className={`alert ${actionMsg.startsWith('⚠️') ? 'alert-error' : 'alert-success'}`} style={{ marginBottom: '1rem' }}>
            {actionMsg}
          </div>
        )}

        {/* Filter Pills */}
        <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
          {[
            { key: 'ALL', label: 'All Payments', count: counts.ALL || 0 },
            { key: 'PENDING', label: '⏳ Pending Verification', count: counts.PENDING || 0 },
            { key: 'APPROVED', label: '✅ Approved', count: counts.APPROVED || 0 },
            { key: 'REJECTED', label: '❌ Rejected', count: counts.REJECTED || 0 },
          ].map(({ key, label, count }) => (
            <button
              key={key}
              onClick={() => setFilter(key as typeof filter)}
              className="btn btn-sm"
              style={{
                background: filter === key ? '#1e3a8a' : '#fff',
                color: filter === key ? '#fff' : '#475569',
                border: '1px solid #cbd5e1',
                borderRadius: '20px',
                padding: '0.4rem 1rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
              }}
            >
              <span>{label}</span>
              <span style={{
                background: filter === key ? '#fff' : '#f1f5f9',
                color: filter === key ? '#1e3a8a' : '#475569',
                padding: '1px 7px',
                borderRadius: '10px',
                fontSize: '0.75rem',
                fontWeight: 700,
              }}>{count}</span>
            </button>
          ))}
        </div>

        {loading && <div style={{ color: '#64748b', padding: '2rem 0' }}>⏳ Loading payment submissions…</div>}
        {error && <div className="alert alert-error">⚠️ {error}</div>}

        {!loading && !error && payments.length === 0 && (
          <div className="card" style={{ textAlign: 'center', padding: '3.5rem' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>💳</div>
            <h3 style={{ color: '#1e293b', marginBottom: '0.5rem' }}>No payment submissions found</h3>
            <p style={{ color: '#64748b' }}>
              {filter === 'ALL'
                ? 'No students have submitted payment proof yet.'
                : `No submissions with status ${filter}.`}
            </p>
          </div>
        )}

        {!loading && !error && payments.length > 0 && (
          <div className="card" style={{ overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1000 }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                  {['Student', 'Event & Fee', 'UPI Transaction ID', 'Proof Receipt', 'Submitted On', 'Status', 'Actions'].map(h => (
                    <th key={h} style={{ padding: '0.85rem 1rem', textAlign: 'left', fontSize: '0.78rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {payments.map(item => (
                  <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <div style={{ fontWeight: 600, color: '#1e293b' }}>{item.studentName ?? `Student #${item.studentId}`}</div>
                      <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        {item.registerNumber ?? '—'} • {item.email ?? ''}
                      </div>
                      {item.department && <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{item.department}</div>}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <div style={{ fontWeight: 600, color: '#1e3a8a' }}>{item.eventTitle ?? `Event #${item.eventId}`}</div>
                      <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#059669', marginTop: '2px' }}>
                        ₹{item.amount}
                      </div>
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <code style={{
                        background: '#f1f5f9', color: '#0f172a', padding: '3px 6px',
                        borderRadius: 4, fontSize: '0.85rem', fontWeight: 600,
                      }}>
                        {item.transactionId}
                      </code>
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => setSelectedProof(item)}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}
                      >
                        🖼️ View Proof
                      </button>
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                      {fmtDate(item.submittedAt)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <span style={{
                        background: item.status === 'APPROVED' ? '#d1fae5' : item.status === 'PENDING' ? '#fef3c7' : '#fee2e2',
                        color: item.status === 'APPROVED' ? '#065f46' : item.status === 'PENDING' ? '#92400e' : '#991b1b',
                        padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700,
                      }}>
                        {item.status === 'APPROVED' ? '✅ Approved' : item.status === 'PENDING' ? '⏳ Pending' : '❌ Rejected'}
                      </span>
                      {item.rejectionReason && (
                        <div style={{ fontSize: '0.75rem', color: '#dc2626', marginTop: '4px', maxWidth: 200 }}>
                          Reason: {item.rejectionReason}
                        </div>
                      )}
                      {item.verifiedByName && (
                        <div style={{ fontSize: '0.72rem', color: '#64748b', marginTop: '2px' }}>
                          By: {item.verifiedByName}
                        </div>
                      )}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      {item.status === 'PENDING' ? (
                        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                          <button
                            className="btn btn-sm"
                            style={{ background: '#16a34a', color: '#fff', border: 'none', borderRadius: 6, padding: '0.3rem 0.75rem', cursor: 'pointer', fontWeight: 600 }}
                            disabled={processingId === item.id}
                            onClick={() => handleApprove(item)}
                          >
                            {processingId === item.id ? '⏳…' : '✅ Approve'}
                          </button>
                          <button
                            className="btn btn-sm"
                            style={{ background: '#dc2626', color: '#fff', border: 'none', borderRadius: 6, padding: '0.3rem 0.75rem', cursor: 'pointer', fontWeight: 600 }}
                            disabled={processingId === item.id}
                            onClick={() => openRejectModal(item)}
                          >
                            ❌ Reject
                          </button>
                        </div>
                      ) : (
                        <span style={{ color: '#94a3b8', fontSize: '0.8rem' }}>Verified</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── View Payment Proof Modal ───────────────────────── */}
      {selectedProof && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', zIndex: 1100, padding: '1rem',
        }} onClick={() => setSelectedProof(null)}>
          <div style={{
            background: '#fff', borderRadius: 12, padding: '1.5rem', width: '100%', maxWidth: 550,
            maxHeight: '90vh', overflow: 'auto', textAlign: 'center',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h3 style={{ margin: 0, color: '#1e3a8a' }}>🧾 Payment Proof Screenshot</h3>
              <button className="btn btn-outline btn-sm" onClick={() => setSelectedProof(null)}>✕ Close</button>
            </div>

            <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: 8, marginBottom: '1rem', textAlign: 'left', fontSize: '0.85rem' }}>
              <div><strong>Student:</strong> {selectedProof.studentName} ({selectedProof.registerNumber})</div>
              <div><strong>Event:</strong> {selectedProof.eventTitle} • <strong>Fee:</strong> ₹{selectedProof.amount}</div>
              <div><strong>Transaction ID:</strong> <code>{selectedProof.transactionId}</code></div>
            </div>

            <div style={{ background: '#f1f5f9', borderRadius: 8, padding: '0.5rem', maxHeight: '55vh', overflow: 'auto' }}>
              <img
                src={getPaymentProofUrl(selectedProof.id)}
                alt="Payment proof screenshot"
                style={{ maxWidth: '100%', maxHeight: '50vh', objectFit: 'contain', borderRadius: 4 }}
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).alt = 'Unable to load payment screenshot';
                }}
              />
            </div>

            <div style={{ marginTop: '1.25rem', display: 'flex', gap: '0.75rem', justifyContent: 'center' }}>
              {selectedProof.status === 'PENDING' && (
                <>
                  <button
                    className="btn btn-navy"
                    onClick={() => {
                      const item = selectedProof;
                      setSelectedProof(null);
                      handleApprove(item);
                    }}
                  >
                    ✅ Approve Payment
                  </button>
                  <button
                    className="btn btn-outline"
                    style={{ color: '#dc2626', borderColor: '#dc2626' }}
                    onClick={() => {
                      const item = selectedProof;
                      setSelectedProof(null);
                      openRejectModal(item);
                    }}
                  >
                    ❌ Reject Payment
                  </button>
                </>
              )}
              <button className="btn btn-outline" onClick={() => setSelectedProof(null)}>Done</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Reject Modal with Reason ───────────────────────── */}
      {rejectingItem && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', zIndex: 1100, padding: '1rem',
        }} onClick={() => setRejectingItem(null)}>
          <div style={{
            background: '#fff', borderRadius: 12, padding: '1.75rem', width: '100%', maxWidth: 480,
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{ color: '#dc2626', marginBottom: '0.5rem' }}>❌ Reject Payment Submission</h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1.25rem' }}>
              Student: <strong>{rejectingItem.studentName}</strong> • Txn ID: <code>{rejectingItem.transactionId}</code>
            </p>

            <form onSubmit={handleConfirmReject}>
              <div className="form-group" style={{ marginBottom: '1.25rem' }}>
                <label className="form-label" style={{ fontWeight: 600 }}>Reason for Rejection *</label>
                <textarea
                  className="form-control"
                  required
                  rows={3}
                  value={rejectReason}
                  onChange={e => setRejectReason(e.target.value)}
                  placeholder="e.g. UTR number not found in bank statement, amount mismatch, or illegible screenshot."
                />
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                <button type="button" className="btn btn-outline" onClick={() => setRejectingItem(null)}>
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn"
                  style={{ background: '#dc2626', color: '#fff', border: 'none', borderRadius: 6, padding: '0.5rem 1.25rem', fontWeight: 600 }}
                  disabled={processingId === rejectingItem.id}
                >
                  {processingId === rejectingItem.id ? '⏳ Rejecting…' : 'Confirm Rejection'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}

