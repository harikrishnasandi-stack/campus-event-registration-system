import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import { apiGetEventById, apiRegisterForEvent, apiGetMyRegistrations, type ApiEvent, type ApiRegistration } from '../api/client';

interface Props {
  eventId: string;
  user: User;
  navigate: (page: Page) => void;
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
  const [event, setEvent]           = useState<ApiEvent | null>(null);
  const [myReg, setMyReg]           = useState<ApiRegistration | null>(null);
  const [loading, setLoading]       = useState(true);
  const [registering, setRegistering] = useState(false);
  const [regMsg, setRegMsg]         = useState('');
  const [regError, setRegError]     = useState('');
  const [error, setError]           = useState('');

  useEffect(() => {
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
  }, [id, user.role]);

  const handleRegister = async () => {
    setRegistering(true);
    setRegMsg('');
    setRegError('');
    try {
      const reg = await apiRegisterForEvent(id);
      setMyReg(reg);
      if (reg.status === 'REGISTERED') {
        setRegMsg('🎉 You are successfully registered!');
        // Update seat count
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
            <p className="page-subtitle">
              <span style={{
                background: event.status === 'PUBLISHED' ? '#16a34a' : '#6366f1',
                color: '#fff', padding: '2px 12px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 700
              }}>
                {event.status}
              </span>
            </p>
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '2rem' }}>

          {/* ── Main content ─────────────────────────────────── */}
          <div>
            {/* Info cards */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
              {[
                { icon: '📅', label: 'Event Date', val: fmtDate(event.eventDate) },
                { icon: '📍', label: 'Venue', val: event.venue },
                { icon: '⏰', label: 'Registration Deadline', val: fmtDate(event.registrationDeadline) },
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
              <div className="card" style={{ padding: '1.5rem' }}>
                <h3 style={{ marginBottom: '0.75rem', color: '#1e3a8a' }}>📋 Instructions for Participants</h3>
                <ol style={{ paddingLeft: '1.25rem', color: '#475569', lineHeight: 2 }}>
                  {instructionLines.map((line, i) => (
                    <li key={i}>{line.replace(/^\d+\.\s*/, '')}</li>
                  ))}
                </ol>
              </div>
            )}
          </div>

          {/* ── Registration sidebar ──────────────────────────── */}
          <div>
            <div className="card" style={{ padding: '1.5rem', position: 'sticky', top: '1.5rem' }}>
              <h3 style={{ marginBottom: '1rem', color: '#1e3a8a' }}>🎟️ Registration</h3>

              {/* Seat bar */}
              <div style={{ marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#64748b', marginBottom: '0.25rem' }}>
                  <span>{event.registeredCount} registered</span>
                  <span>{seatsLeft} left</span>
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

              {/* Messages */}
              {regMsg   && <div className="alert alert-success" style={{ marginBottom: '0.75rem' }}>{regMsg}</div>}
              {regError && <div className="alert alert-error"   style={{ marginBottom: '0.75rem' }}>⚠️ {regError}</div>}

              {/* Student actions */}
              {user.role === 'STUDENT' && event.status === 'PUBLISHED' && (
                <>
                  {myReg ? (
                    <div style={{ textAlign: 'center' }}>
                      <div style={{
                        padding: '0.75rem', borderRadius: 8, marginBottom: '0.5rem',
                        background: myReg.status === 'REGISTERED' ? '#d1fae5' : '#fef3c7',
                        color: myReg.status === 'REGISTERED' ? '#065f46' : '#92400e',
                        fontWeight: 600,
                      }}>
                        {myReg.status === 'REGISTERED' ? '✅ You are Registered' : '⏳ You are Waitlisted'}
                      </div>
                      <p style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        Registered on {new Date(myReg.registeredAt).toLocaleDateString('en-IN')}
                      </p>
                    </div>
                  ) : deadlinePassed ? (
                    <div className="alert" style={{ background: '#fef2f2', color: '#991b1b', border: '1px solid #fecaca' }}>
                      Registration deadline has passed.
                    </div>
                  ) : (
                    <button
                      className="btn btn-navy btn-block"
                      onClick={handleRegister}
                      disabled={registering}
                      style={{ width: '100%' }}
                    >
                      {registering ? '⏳ Registering…' : isFull ? '📋 Join Waitlist' : '🎫 Register Now'}
                    </button>
                  )}
                </>
              )}

              {/* Admin note */}
              {user.role === 'ADMIN' && (
                <div className="alert alert-navy" style={{ fontSize: '0.8rem' }}>
                  👤 Viewing as Admin. Use Admin → Events to manage this event.
                </div>
              )}

              {event.status !== 'PUBLISHED' && (
                <div className="alert" style={{ background: '#f1f5f9', color: '#475569', border: '1px solid #e2e8f0' }}>
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
