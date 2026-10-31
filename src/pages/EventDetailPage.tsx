import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import {
  apiGetEventById,
  apiRegisterForEvent,
  apiGetMyRegistrations,
  apiSubmitPaymentProof,
  getEventQrCodeUrl,
  type ApiEvent,
  type ApiRegistration,
} from '../api/client';

interface Props {
  eventId: string;
  user: User;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', {
    weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function isPast(iso: string) {
  return new Date(iso) < new Date();
}

export default function EventDetailPage({ eventId, user, navigate }: Props) {
  const id = Number(eventId);
  const [event, setEvent]               = useState<ApiEvent | null>(null);
  const [myReg, setMyReg]               = useState<ApiRegistration | null>(null);
  const [loading, setLoading]           = useState(true);
  const [registering, setRegistering]   = useState(false);
  const [regMsg, setRegMsg]             = useState('');
  const [regError, setRegError]         = useState('');
  const [error, setError]               = useState('');

  // Payment proof form state
  const [txnId, setTxnId]               = useState('');
  const [proofFile, setProofFile]       = useState<File | null>(null);
  const [proofPreview, setProofPreview] = useState<string | null>(null);
  const [submittingPay, setSubmittingPay] = useState(false);
  const [paySuccessMsg, setPaySuccessMsg] = useState('');
  const [payErrorMsg, setPayErrorMsg]   = useState('');
  const [qrLoaded, setQrLoaded]         = useState(false);
  const [qrFailed, setQrFailed]         = useState(false);

  const loadData = () => {
    setLoading(true);
    Promise.all([
      apiGetEventById(id),
      user.role === 'STUDENT' ? apiGetMyRegistrations() : Promise.resolve([] as ApiRegistration[]),
    ])
      .then(([ev, regs]) => {
        setEvent(ev);
        const found = (regs as ApiRegistration[]).find(r => r.eventId === id);
        if (found) setMyReg(found);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, [id, user.role]);

  // Free event registration
  const handleRegister = async () => {
    setRegistering(true);
    setRegMsg('');
    setRegError('');
    try {
      const reg = await apiRegisterForEvent(id);
      setMyReg(reg);
      if (reg.status === 'REGISTERED') {
        setRegMsg('🎉 You are successfully registered!');
        setEvent(ev => ev ? { ...ev, registeredCount: ev.registeredCount + 1 } : ev);
      } else {
        setRegMsg('⏳ Seats are full — you have been added to the waitlist.');
      }
    } catch (err) {
      setRegError(err instanceof Error ? err.message : 'Registration failed.');
    } finally {
      setRegistering(false);
    }
  };

  const handleProofChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setProofFile(file);
      setProofPreview(URL.createObjectURL(file));
      setPayErrorMsg('');
    }
  };

  // Paid event proof submission
  const handleSubmitPaymentProof = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!txnId.trim()) {
      setPayErrorMsg('Please enter your UPI Transaction ID / UTR Number.');
      return;
    }
    if (!proofFile) {
      setPayErrorMsg('Please select your payment screenshot / receipt image.');
      return;
    }

    setSubmittingPay(true);
    setPaySuccessMsg('');
    setPayErrorMsg('');

    try {
      const res = await apiSubmitPaymentProof(id, txnId.trim(), proofFile);
      setPaySuccessMsg(res.message);
      // Immediately reflect pending registration state in UI
      setMyReg({
        id: res.submission.registrationId,
        studentId: res.submission.studentId,
        eventId: id,
        status: 'PENDING',
        paymentStatus: 'PENDING',
        amount: res.submission.amount,
        transactionId: res.submission.transactionId,
        registeredAt: new Date().toISOString(),
        eventTitle: event?.title,
      });
      // Clear form
      setTxnId('');
      setProofFile(null);
      setProofPreview(null);
    } catch (err) {
      setPayErrorMsg(err instanceof Error ? err.message : 'Failed to submit payment proof.');
    } finally {
      setSubmittingPay(false);
    }
  };

  if (loading) return (
    <div style={{ textAlign: 'center', padding: '4rem', color: '#64748b' }}>
      ⏳ Loading event details…
    </div>
  );
  if (error) return (
    <div className="container page-section">
      <div className="alert alert-error">⚠️ {error}</div>
      <button className="btn btn-outline" onClick={() => navigate('events')}>← Back to Events</button>
    </div>
  );
  if (!event) return null;

  const isPaid = Number(event.fee) > 0;
  const seatsLeft = event.totalSeats - event.registeredCount;
  const isFull    = seatsLeft <= 0;
  const deadlinePassed = isPast(event.registrationDeadline);
  const instructionLines = event.instructions
    ? event.instructions.split('\n').filter(l => l.trim())
    : [];

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <button className="btn btn-outline btn-sm" onClick={() => navigate('events')} style={{ marginBottom: '0.5rem' }}>
              ← Back to Events
            </button>
            <h1 className="page-title">{event.title}</h1>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.25rem' }}>
              <span style={{
                background: event.status === 'PUBLISHED' ? '#16a34a' : '#6366f1',
                color: '#fff', padding: '2px 12px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 700
              }}>
                {event.status}
              </span>
              <span style={{
                background: isPaid ? '#ecfdf5' : '#f1f5f9',
                color: isPaid ? '#047857' : '#475569',
                border: isPaid ? '1px solid #a7f3d0' : '1px solid #cbd5e1',
                padding: '2px 12px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 700
              }}>
                {isPaid ? `₹${event.fee} Entry Fee` : 'FREE EVENT'}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: '2rem' }}>

          {/* ── Main content ─────────────────────────────────── */}
          <div>
            {/* Info cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
              {[
                { icon: '📅', label: 'Event Date', val: fmtDate(event.eventDate) },
                { icon: '📍', label: 'Venue', val: event.venue || 'TBA' },
                { icon: '⏰', label: 'Registration Deadline', val: fmtDate(event.registrationDeadline) },
                { icon: '💰', label: 'Event Fee', val: isPaid ? `₹${event.fee}` : 'FREE' },
                { icon: '💺', label: 'Seats', val: `${seatsLeft} of ${event.totalSeats} available` },
              ].map(({ icon, label, val }) => (
                <div key={label} className="card" style={{ padding: '1rem' }}>
                  <div style={{ fontSize: '1.5rem', marginBottom: '0.25rem' }}>{icon}</div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '0.25rem' }}>{label}</div>
                  <div style={{ fontWeight: 600, color: '#1e293b' }}>{val}</div>
                </div>
              ))}
            </div>

            {/* Description */}
            <div className="card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
              <h3 style={{ marginBottom: '0.75rem', color: '#1e3a8a' }}>📄 About This Event</h3>
              <p style={{ color: '#475569', lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>{event.description}</p>
            </div>

            {/* Instructions */}
            {instructionLines.length > 0 && (
              <div className="card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
                <h3 style={{ marginBottom: '0.75rem', color: '#1e3a8a' }}>📋 Instructions for Participants</h3>
                <ol style={{ paddingLeft: '1.25rem', color: '#475569', lineHeight: 2 }}>
                  {instructionLines.map((line, i) => (
                    <li key={i}>{line.replace(/^\d+\.\s*/, '')}</li>
                  ))}
                </ol>
              </div>
            )}

            {/* UPI QR Code Showcase for Paid Events */}
            {isPaid && (
              <div className="card" style={{ padding: '1.5rem', background: '#f8fafc', border: '1px solid #e2e8f0' }}>
                <h3 style={{ marginBottom: '0.5rem', color: '#1e3a8a' }}>📱 UPI Payment Details</h3>
                <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1rem' }}>
                  Scan the official QR code below using any UPI app (Google Pay, PhonePe, Paytm, BHIM) and pay <strong>₹{event.fee}</strong>.
                </p>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
                  <div style={{
                    background: '#fff', padding: '0.75rem', borderRadius: 12,
                    boxShadow: '0 2px 8px rgba(0,0,0,0.06)', border: '1px solid #cbd5e1',
                    position: 'relative', width: 190, height: 190, display: 'flex',
                    alignItems: 'center', justifyContent: 'center', overflow: 'hidden'
                  }}>
                    {!qrLoaded && !qrFailed && (
                      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64748b', fontSize: '0.85rem' }}>
                        ⏳ Loading QR…
                      </div>
                    )}
                    {qrFailed ? (
                      <div style={{ textAlign: 'center', padding: '0.5rem', color: '#dc2626' }}>
                        <div style={{ fontSize: '1.75rem', marginBottom: '0.25rem' }}>⚠️</div>
                        <div style={{ fontSize: '0.78rem', fontWeight: 600 }}>QR Code unavailable</div>
                        <button
                          type="button"
                          className="btn btn-outline btn-sm"
                          style={{ marginTop: '0.5rem', fontSize: '0.72rem', padding: '2px 8px' }}
                          onClick={() => { setQrFailed(false); setQrLoaded(false); }}
                        >
                          🔄 Retry
                        </button>
                      </div>
                    ) : (
                      <img
                        key={event.upiQrImage || event.id}
                        src={getEventQrCodeUrl(event.id, event.upiQrImage)}
                        alt="Admin UPI QR Code"
                        style={{
                          width: '100%', height: '100%', objectFit: 'contain', display: 'block',
                          opacity: qrLoaded ? 1 : 0, transition: 'opacity 0.2s ease-in'
                        }}
                        onLoad={() => { setQrLoaded(true); setQrFailed(false); }}
                        onError={() => { setQrFailed(true); setQrLoaded(false); }}
                      />
                    )}
                  </div>
                  <div style={{ flex: 1, minWidth: 220 }}>
                    <div style={{ fontSize: '0.9rem', color: '#1e293b', marginBottom: '0.4rem' }}>
                      <strong>Fee Amount:</strong> ₹{event.fee}
                    </div>
                    <div style={{ fontSize: '0.85rem', color: '#64748b', lineHeight: 1.6 }}>
                      1. Scan the QR code and pay the exact amount (₹{event.fee}).<br/>
                      2. Note down the 12-digit UPI / UTR Transaction ID.<br/>
                      3. Take a screenshot of the payment receipt.<br/>
                      4. Submit the transaction ID and screenshot in the form on the right.
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* ── Registration / Payment Sidebar ─────────────────── */}
          <div>
            <div className="card" style={{ padding: '1.5rem', position: 'sticky', top: '1.5rem' }}>
              <h3 style={{ marginBottom: '1rem', color: '#1e3a8a' }}>
                {isPaid ? '💳 Payment & Registration' : '🎟️ Registration'}
              </h3>

              {/* Seat bar */}
              <div style={{ marginBottom: '1.25rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#64748b', marginBottom: '0.25rem' }}>
                  <span>{event.registeredCount} registered</span>
                  <span>{seatsLeft} seats left</span>
                </div>
                <div style={{ background: '#e2e8f0', borderRadius: 4, height: 8 }}>
                  <div style={{
                    background: isFull ? '#dc2626' : '#1e3a8a',
                    borderRadius: 4, height: 8,
                    width: `${Math.min(100, (event.registeredCount / event.totalSeats) * 100)}%`,
                    transition: 'width 0.3s',
                  }} />
                </div>
              </div>

              {/* Free event status/action */}
              {user.role === 'STUDENT' && !isPaid && event.status === 'PUBLISHED' && (
                <>
                  {regMsg   && <div className="alert alert-success" style={{ marginBottom: '0.75rem' }}>{regMsg}</div>}
                  {regError && <div className="alert alert-error"   style={{ marginBottom: '0.75rem' }}>⚠️ {regError}</div>}

                  {myReg ? (
                    <div style={{ textAlign: 'center', background: '#d1fae5', padding: '1rem', borderRadius: 8 }}>
                      <div style={{ fontWeight: 700, color: '#065f46', marginBottom: '0.25rem' }}>
                        {myReg.status === 'REGISTERED' ? '✅ You are Registered' : '⏳ You are Waitlisted'}
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#065f46' }}>
                        Registered on {new Date(myReg.registeredAt).toLocaleDateString('en-IN')}
                      </div>
                      <button
                        className="btn btn-outline btn-sm"
                        style={{ marginTop: '0.75rem', background: '#fff' }}
                        onClick={() => navigate('my-registrations')}
                      >
                        View My Registrations →
                      </button>
                    </div>
                  ) : deadlinePassed ? (
                    <div className="alert alert-error">Registration deadline has passed.</div>
                  ) : (
                    <button
                      className="btn btn-navy btn-block"
                      onClick={handleRegister}
                      disabled={registering}
                      style={{ width: '100%' }}
                    >
                      {registering ? '⏳ Registering…' : isFull ? '📋 Join Waitlist' : '🎫 Register Now (Free)'}
                    </button>
                  )}
                </>
              )}

              {/* Paid event status/action */}
              {user.role === 'STUDENT' && isPaid && event.status === 'PUBLISHED' && (
                <>
                  {paySuccessMsg && (
                    <div className="alert alert-success" style={{ marginBottom: '1rem' }}>
                      ✅ {paySuccessMsg}
                    </div>
                  )}
                  {payErrorMsg && (
                    <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
                      ⚠️ {payErrorMsg}
                    </div>
                  )}

                  {/* Case 1: Confirmed / Approved Registration */}
                  {myReg && (myReg.status === 'REGISTERED' || myReg.paymentStatus === 'APPROVED') ? (
                    <div style={{ textAlign: 'center', background: '#d1fae5', padding: '1.25rem', borderRadius: 8, border: '1px solid #a7f3d0' }}>
                      <div style={{ fontSize: '1.8rem', marginBottom: '0.25rem' }}>🎉</div>
                      <div style={{ fontWeight: 700, color: '#065f46', fontSize: '1rem', marginBottom: '0.25rem' }}>
                        Registration Confirmed!
                      </div>
                      <div style={{ fontSize: '0.82rem', color: '#047857', marginBottom: '1rem' }}>
                        Your payment of ₹{event.fee} has been verified and approved by the admin.
                      </div>
                      <button
                        className="btn btn-navy btn-sm"
                        style={{ width: '100%' }}
                        onClick={() => navigate('my-registrations')}
                      >
                        🧾 View & Download Receipt →
                      </button>
                    </div>
                  ) : myReg && (myReg.status === 'PENDING' || myReg.paymentStatus === 'PENDING') ? (
                    /* Case 2: Pending Admin Verification */
                    <div style={{ textAlign: 'center', background: '#fef3c7', padding: '1.25rem', borderRadius: 8, border: '1px solid #fde68a' }}>
                      <div style={{ fontSize: '1.8rem', marginBottom: '0.25rem' }}>⏳</div>
                      <div style={{ fontWeight: 700, color: '#92400e', fontSize: '0.95rem', marginBottom: '0.4rem' }}>
                        Payment Proof Submitted
                      </div>
                      <div style={{ fontSize: '0.82rem', color: '#78350f', lineHeight: 1.5, marginBottom: '0.75rem' }}>
                        Your registration is awaiting admin verification. Once verified, your status will update to Confirmed and your receipt will be available.
                      </div>
                      {myReg.transactionId && (
                        <div style={{ fontSize: '0.75rem', background: '#fff', padding: '4px 8px', borderRadius: 4, display: 'inline-block', color: '#1e293b' }}>
                          Txn ID: <strong>{myReg.transactionId}</strong>
                        </div>
                      )}
                    </div>
                  ) : deadlinePassed ? (
                    <div className="alert alert-error">Registration deadline has passed.</div>
                  ) : (
                    /* Case 3: Need to submit payment proof (first time or after rejection) */
                    <div>
                      {myReg && myReg.paymentStatus === 'REJECTED' && (
                        <div className="alert alert-error" style={{ marginBottom: '1rem', fontSize: '0.85rem' }}>
                          <strong>⚠️ Previous Payment Rejected:</strong> {myReg.rejectionReason ?? 'Please check your transaction details and resubmit valid proof below.'}
                        </div>
                      )}

                      <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: 8, marginBottom: '1rem', fontSize: '0.85rem' }}>
                        <div style={{ color: '#1e3a8a', fontWeight: 600 }}>Amount Payable: ₹{event.fee}</div>
                        <div style={{ color: '#64748b', fontSize: '0.78rem', marginTop: '2px' }}>
                          Scan the QR code on the left, then fill this proof submission form.
                        </div>
                      </div>

                      <form onSubmit={handleSubmitPaymentProof}>
                        <div className="form-group" style={{ marginBottom: '1rem' }}>
                          <label className="form-label" style={{ fontWeight: 600, fontSize: '0.85rem' }}>
                            UPI Transaction ID / UTR *
                          </label>
                          <input
                            type="text"
                            className="form-control"
                            required
                            value={txnId}
                            onChange={e => setTxnId(e.target.value)}
                            placeholder="e.g. 412356789012"
                            style={{ fontSize: '0.85rem' }}
                          />
                          <small style={{ color: '#64748b', fontSize: '0.72rem' }}>
                            12-digit UPI reference/UTR number from your payment app
                          </small>
                        </div>

                        <div className="form-group" style={{ marginBottom: '1.25rem' }}>
                          <label className="form-label" style={{ fontWeight: 600, fontSize: '0.85rem' }}>
                            Payment Screenshot / Receipt *
                          </label>
                          <input
                            type="file"
                            accept="image/png,image/jpeg,image/jpg"
                            required
                            onChange={handleProofChange}
                            className="form-control"
                            style={{ fontSize: '0.82rem' }}
                          />
                          <small style={{ color: '#64748b', fontSize: '0.72rem' }}>
                            Supported formats: PNG, JPG, JPEG (max 5 MB)
                          </small>

                          {proofPreview && (
                            <div style={{ marginTop: '0.5rem', textAlign: 'center' }}>
                              <img
                                src={proofPreview}
                                alt="Payment proof preview"
                                style={{ maxHeight: 110, maxWidth: '100%', borderRadius: 6, border: '1px solid #cbd5e1' }}
                              />
                            </div>
                          )}
                        </div>

                        <button
                          type="submit"
                          className="btn btn-navy"
                          disabled={submittingPay || isFull}
                          style={{ width: '100%', padding: '0.65rem' }}
                        >
                          {submittingPay ? '⏳ Submitting Proof…' : isFull ? 'Event Seats Full' : '📤 Submit Payment Proof'}
                        </button>
                      </form>
                    </div>
                  )}
                </>
              )}

              {/* Admin Note */}
              {user.role === 'ADMIN' && (
                <div className="alert alert-navy" style={{ fontSize: '0.8rem', marginTop: '1rem' }}>
                  👤 Viewing as Admin. Use <strong>Admin → Events</strong> or <strong>Payment Verification</strong> to manage.
                </div>
              )}

              {event.status !== 'PUBLISHED' && (
                <div className="alert" style={{ background: '#f1f5f9', color: '#475569', border: '1px solid #e2e8f0', marginTop: '1rem' }}>
                  Registration is not available for {event.status.toLowerCase()} events.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
