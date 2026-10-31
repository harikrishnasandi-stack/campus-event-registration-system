import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import { apiGetMyRegistrations, apiGetReceipt, type ApiRegistration, type ApiReceipt } from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

export default function MyRegistrations({ user, navigate }: Props) {
  const [regs, setRegs]               = useState<ApiRegistration[]>([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');
  const [receiptData, setReceiptData] = useState<ApiReceipt | null>(null);
  const [receiptLoading, setReceiptLoading] = useState(false);
  const [receiptError, setReceiptError] = useState('');

  useEffect(() => {
    apiGetMyRegistrations()
      .then(setRegs)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const goToEvent = (eventId: number) =>
    navigate('event-detail', { selectedEventId: String(eventId) });

  const handleOpenReceipt = async (r: ApiRegistration) => {
    setReceiptError('');
    setReceiptLoading(true);
    try {
      if (r.paymentStatus === 'APPROVED') {
        const data = await apiGetReceipt(r.id);
        setReceiptData(data);
      } else {
        // Fallback receipt for free confirmed event
        setReceiptData({
          systemTitle: 'Campus Event Management System',
          institution: 'Kalasalingam Academy of Research and Education',
          receiptNumber: `REC-FREE-${r.id}`,
          registrationId: `REG-${r.id}`,
          studentName: user.name,
          registerNumber: user.registerNumber ?? '—',
          email: user.email,
          department: user.department ?? 'N/A',
          eventTitle: r.eventTitle ?? `Event #${r.eventId}`,
          eventDate: r.eventDate ? fmtDate(r.eventDate) : '—',
          venue: r.venue ?? 'Campus Venue',
          amount: 0,
          transactionId: 'FREE_EVENT',
          paymentStatus: 'CONFIRMED (FREE)',
          verifiedBy: 'System Auto-Approval',
          submittedAt: fmtDate(r.registeredAt),
          approvalDate: fmtDate(r.registeredAt),
        });
      }
    } catch (err) {
      setReceiptError(err instanceof Error ? err.message : 'Failed to load receipt.');
    } finally {
      setReceiptLoading(false);
    }
  };

  const renderStatusBadge = (r: ApiRegistration) => {
    if (r.paymentStatus === 'PENDING') {
      return (
        <div>
          <span style={{ background: '#fef3c7', color: '#92400e', padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700 }}>
            ⏳ Payment Pending
          </span>
          <div style={{ fontSize: '0.72rem', color: '#64748b', marginTop: '2px' }}>Awaiting admin review</div>
        </div>
      );
    }
    if (r.paymentStatus === 'REJECTED') {
      return (
        <div>
          <span style={{ background: '#fee2e2', color: '#991b1b', padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700 }}>
            ❌ Payment Rejected
          </span>
          {r.rejectionReason && (
            <div style={{ fontSize: '0.72rem', color: '#dc2626', marginTop: '2px', maxWidth: 180 }}>
              {r.rejectionReason}
            </div>
          )}
        </div>
      );
    }
    if (r.paymentStatus === 'APPROVED' || r.status === 'REGISTERED') {
      return (
        <div>
          <span style={{ background: '#d1fae5', color: '#065f46', padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700 }}>
            ✅ Confirmed
          </span>
          {r.paymentStatus === 'APPROVED' && (
            <div style={{ fontSize: '0.72rem', color: '#047857', marginTop: '2px' }}>Payment Verified</div>
          )}
        </div>
      );
    }
    if (r.status === 'WAITLISTED') {
      return (
        <span style={{ background: '#fef3c7', color: '#92400e', padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700 }}>
          ⏳ Waitlisted
        </span>
      );
    }
    return (
      <span style={{ background: '#fee2e2', color: '#991b1b', padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700 }}>
        {r.status}
      </span>
    );
  };

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <h1 className="page-title">📋 My Registrations</h1>
            <p className="page-subtitle">
              {loading ? 'Loading…' : `${regs.length} registration${regs.length !== 1 ? 's' : ''}`}
            </p>
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {loading && (
          <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>⏳ Loading registrations…</div>
        )}
        {error && <div className="alert alert-error">⚠️ {error}</div>}
        {receiptError && <div className="alert alert-error" style={{ marginBottom: '1rem' }}>⚠️ {receiptError}</div>}

        {!loading && !error && regs.length === 0 && (
          <div style={{ textAlign: 'center', padding: '4rem' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>📋</div>
            <h3 style={{ color: '#1e293b', marginBottom: '0.5rem' }}>No registrations yet</h3>
            <p style={{ color: '#64748b', marginBottom: '1.5rem' }}>
              Browse upcoming events and register to see them here.
            </p>
            <button className="btn btn-navy" onClick={() => navigate('events')}>
              🎪 Browse Events
            </button>
          </div>
        )}

        {!loading && !error && regs.length > 0 && (
          <div className="card" style={{ overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 900 }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                  {['#', 'Event', 'Date', 'Venue', 'Registered On', 'Status', 'Actions'].map(h => (
                    <th key={h} style={{ padding: '0.85rem 1rem', textAlign: 'left', fontSize: '0.8rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {regs.map((r, i) => (
                  <tr key={r.id} style={{ borderBottom: '1px solid #f1f5f9' }}
                    onMouseEnter={e => (e.currentTarget.style.background = '#f8fafc')}
                    onMouseLeave={e => (e.currentTarget.style.background = '')}>
                    <td style={{ padding: '0.85rem 1rem', color: '#94a3b8', fontSize: '0.85rem' }}>{i + 1}</td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <div style={{ fontWeight: 600, color: '#1e293b' }}>
                        {r.eventTitle ?? `Event #${r.eventId}`}
                      </div>
                      {r.amount !== undefined && Number(r.amount) > 0 && (
                        <div style={{ fontSize: '0.78rem', color: '#059669', fontWeight: 600 }}>Fee: ₹{r.amount}</div>
                      )}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                      {r.eventDate ? fmtDate(r.eventDate) : '—'}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>
                      {r.venue ?? '—'}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                      {fmtDate(r.registeredAt)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      {renderStatusBadge(r)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                        <button
                          className="btn btn-outline btn-sm"
                          onClick={() => goToEvent(r.eventId)}
                        >
                          View →
                        </button>
                        {r.paymentStatus === 'REJECTED' && (
                          <button
                            className="btn btn-sm"
                            style={{ background: '#ea580c', color: '#fff', border: 'none', borderRadius: 6, padding: '0.25rem 0.6rem', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer' }}
                            onClick={() => goToEvent(r.eventId)}
                          >
                            🔄 Resubmit
                          </button>
                        )}
                        {(r.paymentStatus === 'APPROVED' || (r.status === 'REGISTERED' && r.paymentStatus !== 'PENDING' && r.paymentStatus !== 'REJECTED')) && (
                          <button
                            className="btn btn-sm"
                            style={{ background: '#1e3a8a', color: '#fff', border: 'none', borderRadius: 6, padding: '0.25rem 0.6rem', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer' }}
                            disabled={receiptLoading}
                            onClick={() => handleOpenReceipt(r)}
                          >
                            🧾 Receipt
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Official Registration Receipt Modal ───────────────── */}
      {receiptData && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', zIndex: 1100, padding: '1rem',
        }} onClick={() => setReceiptData(null)}>
          <div style={{
            background: '#fff', borderRadius: 12, padding: '2rem', width: '100%', maxWidth: 580,
            maxHeight: '92vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>

            {/* Printable Receipt Card */}
            <div id="registration-receipt-print" style={{
              border: '2px solid #1e3a8a', borderRadius: 8, padding: '1.75rem', background: '#fafaf9',
              fontFamily: 'system-ui, -apple-system, sans-serif'
            }}>
              {/* Header */}
              <div style={{ textAlign: 'center', borderBottom: '2px solid #cbd5e1', paddingBottom: '1rem', marginBottom: '1.25rem' }}>
                <div style={{ fontSize: '1.8rem', marginBottom: '0.25rem' }}>🎓</div>
                <h2 style={{ margin: 0, color: '#1e3a8a', fontSize: '1.35rem' }}>{receiptData.systemTitle}</h2>
                <div style={{ fontSize: '0.85rem', color: '#475569', fontWeight: 600, marginTop: '2px' }}>
                  {receiptData.institution}
                </div>
                <div style={{
                  display: 'inline-block', marginTop: '0.6rem', background: '#d1fae5', color: '#065f46',
                  padding: '2px 14px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 800, letterSpacing: '0.05em'
                }}>
                  OFFICIAL REGISTRATION RECEIPT
                </div>
              </div>

              {/* Meta Grid */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
                <div>
                  <div style={{ color: '#64748b', fontSize: '0.75rem' }}>Receipt No</div>
                  <div style={{ fontWeight: 700, color: '#0f172a' }}>{receiptData.receiptNumber}</div>
                </div>
                <div>
                  <div style={{ color: '#64748b', fontSize: '0.75rem' }}>Registration ID</div>
                  <div style={{ fontWeight: 700, color: '#0f172a' }}>{receiptData.registrationId}</div>
                </div>
              </div>

              {/* Student Info */}
              <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 6, padding: '0.85rem', marginBottom: '1rem', fontSize: '0.85rem' }}>
                <div style={{ fontWeight: 700, color: '#1e3a8a', marginBottom: '0.4rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.25rem' }}>
                  👤 Participant Details
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.4rem' }}>
                  <div><strong>Name:</strong> {receiptData.studentName}</div>
                  <div><strong>Reg No:</strong> {receiptData.registerNumber}</div>
                  <div><strong>Department:</strong> {receiptData.department}</div>
                  <div><strong>Email:</strong> {receiptData.email}</div>
                </div>
              </div>

              {/* Event Info */}
              <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 6, padding: '0.85rem', marginBottom: '1rem', fontSize: '0.85rem' }}>
                <div style={{ fontWeight: 700, color: '#1e3a8a', marginBottom: '0.4rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.25rem' }}>
                  🎪 Event Details
                </div>
                <div style={{ marginBottom: '0.25rem' }}><strong>Event:</strong> {receiptData.eventTitle}</div>
                <div style={{ marginBottom: '0.25rem' }}><strong>Date:</strong> {receiptData.eventDate}</div>
                <div><strong>Venue:</strong> {receiptData.venue}</div>
              </div>

              {/* Payment Details */}
              <div style={{ background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: 6, padding: '0.85rem', marginBottom: '1.25rem', fontSize: '0.85rem' }}>
                <div style={{ fontWeight: 700, color: '#065f46', marginBottom: '0.4rem', borderBottom: '1px solid #d1fae5', paddingBottom: '0.25rem' }}>
                  💳 Verification & Payment Audit
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.4rem' }}>
                  <div><strong>Amount Paid:</strong> ₹{receiptData.amount}</div>
                  <div><strong>Status:</strong> <span style={{ color: '#059669', fontWeight: 800 }}>{receiptData.paymentStatus}</span></div>
                  <div><strong>Txn / UTR ID:</strong> <code>{receiptData.transactionId}</code></div>
                  <div><strong>Verified On:</strong> {receiptData.approvalDate}</div>
                </div>
              </div>

              {/* Stamp & Footer */}
              <div style={{ textAlign: 'center', fontSize: '0.75rem', color: '#64748b', borderTop: '1px dashed #cbd5e1', paddingTop: '0.75rem' }}>
                <div>Verified by: <strong>{receiptData.verifiedBy}</strong></div>
                <div style={{ marginTop: '2px' }}>This is an authentic computer-generated receipt valid for college event entry.</div>
              </div>
            </div>

            {/* Actions */}
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
              <button className="btn btn-outline" onClick={() => setReceiptData(null)}>
                Close
              </button>
              <button
                className="btn btn-navy"
                onClick={() => window.print()}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
              >
                🖨️ Print / Save PDF
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
