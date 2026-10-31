import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import { apiGetAdminEvents, apiGetAdminPaymentCounts, type ApiEvent } from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function AdminDashboard({ navigate }: Props) {
  const [events, setEvents]           = useState<ApiEvent[]>([]);
  const [pendingPayments, setPendingPayments] = useState(0);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');

  useEffect(() => {
    Promise.all([
      apiGetAdminEvents(),
      apiGetAdminPaymentCounts(),
    ])
      .then(([evs, counts]) => {
        setEvents(evs);
        setPendingPayments(counts.PENDING || 0);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const published  = events.filter(e => e.status === 'PUBLISHED').length;
  const completed  = events.filter(e => e.status === 'COMPLETED').length;
  const draft      = events.filter(e => e.status === 'DRAFT').length;
  const totalSeats = events.reduce((s, e) => s + e.totalSeats, 0);
  const totalRegs  = events.reduce((s, e) => s + e.registeredCount, 0);

  return (
    <>
      <div className="welcome-bar">
        <div className="welcome-inner">
          <div>
            <h1 className="welcome-title">🎛️ Admin Dashboard</h1>
            <p className="welcome-sub">Campus Event Management System</p>
          </div>
          <div className="welcome-pill">
            🗓️ {new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: 0 }}>
        {/* Stats */}
        <div className="stats-grid" style={{ marginBottom: '1.75rem' }}>
          {[
            { icon: '🎪', label: 'Total Events', val: events.length, color: 'icon-blue', page: 'admin-events' as Page },
            { icon: '📢', label: 'Published', val: published, color: 'icon-green', page: 'admin-events' as Page },
            { icon: '📝', label: 'Drafts', val: draft, color: 'icon-orange', page: 'admin-events' as Page },
            { icon: '✅', label: 'Completed', val: completed, color: 'icon-purple', page: 'admin-events' as Page },
          ].map(s => (
            <div key={s.label} className="stat-card" style={{ cursor: 'pointer' }} onClick={() => navigate(s.page)}>
              <div className={`stat-icon ${s.color}`}>{s.icon}</div>
              <div>
                <div className="stat-val">{s.val}</div>
                <div className="stat-lbl">{s.label}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Registration stats */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '2rem' }}>
          <div className="card" style={{ padding: '1.5rem' }}>
            <div style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>💺</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: '#1e3a8a' }}>{totalRegs}</div>
            <div style={{ color: '#64748b', fontSize: '0.875rem' }}>Total Registrations across all events</div>
            <div style={{ color: '#94a3b8', fontSize: '0.8rem', marginTop: '0.25rem' }}>
              out of {totalSeats} total seats ({totalSeats > 0 ? Math.round((totalRegs / totalSeats) * 100) : 0}% fill rate)
            </div>
          </div>
          <div className="card" style={{ padding: '1.5rem' }}>
            <div style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>🚀</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: '#16a34a' }}>{published}</div>
            <div style={{ color: '#64748b', fontSize: '0.875rem' }}>Active published events</div>
            <div style={{ color: '#94a3b8', fontSize: '0.8rem', marginTop: '0.25rem' }}>
              Students can register for these now
            </div>
          </div>
        </div>

        {/* Quick actions */}
        <div className="section-header" style={{ marginBottom: '1rem' }}>
          <h2 className="section-title">⚡ Quick Actions</h2>
        </div>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
          <button className="btn btn-navy" onClick={() => navigate('admin-events')}>
            🎪 Manage Events
          </button>
          <button
            className="btn btn-outline"
            onClick={() => navigate('admin-payments')}
            style={{ position: 'relative' }}
          >
            💳 Verify Payments
            {pendingPayments > 0 && (
              <span style={{
                marginLeft: 8, background: '#dc2626', color: '#fff', padding: '2px 8px',
                borderRadius: 12, fontSize: '0.75rem', fontWeight: 700
              }}>
                {pendingPayments} pending
              </span>
            )}
          </button>
          <button className="btn btn-outline" onClick={() => navigate('admin-certificates')}>
            📜 Upload Certificates
          </button>
        </div>

        {/* Recent events table */}
        <div className="section-header">
          <h2 className="section-title">🎪 All Events</h2>
          <button className="btn btn-outline btn-sm" onClick={() => navigate('admin-events')}>Manage →</button>
        </div>

        {loading && <div style={{ color: '#64748b', padding: '1rem 0' }}>⏳ Loading events…</div>}
        {error && <div className="alert alert-error">⚠️ {error}</div>}

        {!loading && !error && (
          <div className="card" style={{ overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                  {['Title', 'Date', 'Venue', 'Seats', 'Registered', 'Status', 'Actions'].map(h => (
                    <th key={h} style={{ padding: '0.85rem 1rem', textAlign: 'left', fontSize: '0.78rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {events.map(ev => (
                  <tr key={ev.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.85rem 1rem', fontWeight: 600, color: '#1e293b' }}>{ev.title}</td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>{fmtDate(ev.eventDate)}</td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>{ev.venue}</td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>{ev.totalSeats}</td>
                    <td style={{ padding: '0.85rem 1rem', fontWeight: 600, color: '#1e3a8a' }}>{ev.registeredCount}</td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <span style={{
                        background: ev.status === 'PUBLISHED' ? '#d1fae5' : ev.status === 'DRAFT' ? '#fef3c7' : ev.status === 'COMPLETED' ? '#ede9fe' : '#fee2e2',
                        color: ev.status === 'PUBLISHED' ? '#065f46' : ev.status === 'DRAFT' ? '#92400e' : ev.status === 'COMPLETED' ? '#4c1d95' : '#991b1b',
                        padding: '2px 10px', borderRadius: 12, fontSize: '0.75rem', fontWeight: 700,
                      }}>{ev.status}</span>
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => navigate('admin-participants', { selectedEventId: String(ev.id) })}
                      >
                        Participants →
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
