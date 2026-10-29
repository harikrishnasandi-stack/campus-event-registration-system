import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import { apiGetMyRegistrations, type ApiRegistration } from '../api/client';

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

function statusBadge(status: string) {
  const map: Record<string, { bg: string; color: string; label: string }> = {
    REGISTERED:  { bg: '#d1fae5', color: '#065f46', label: '✅ Registered' },
    WAITLISTED:  { bg: '#fef3c7', color: '#92400e', label: '⏳ Waitlisted' },
    CANCELLED:   { bg: '#fee2e2', color: '#991b1b', label: '❌ Cancelled'  },
  };
  const s = map[status] ?? { bg: '#f1f5f9', color: '#475569', label: status };
  return (
    <span style={{ background: s.bg, color: s.color, padding: '3px 10px', borderRadius: 12, fontSize: '0.78rem', fontWeight: 700 }}>
      {s.label}
    </span>
  );
}

export default function MyRegistrations({ navigate }: Props) {
  const [regs, setRegs]       = useState<ApiRegistration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');

  useEffect(() => {
    apiGetMyRegistrations()
      .then(setRegs)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const goToEvent = (eventId: number) =>
    navigate('event-detail', { selectedEventId: String(eventId) });

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
          <div className="card" style={{ overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                  {['#', 'Event', 'Date', 'Venue', 'Registered On', 'Status', ''].map(h => (
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
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>
                      {r.eventDate ? fmtDate(r.eventDate) : '—'}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>
                      {r.venue ?? '—'}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>
                      {fmtDate(r.registeredAt)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      {statusBadge(r.status)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => goToEvent(r.eventId)}
                      >
                        View →
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
