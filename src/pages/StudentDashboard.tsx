import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import {
  apiGetEvents, apiGetMyRegistrations, apiGetMyCertificates, apiGetMyNotifications,
  type ApiEvent, type ApiRegistration, type ApiNotification,
} from '../api/client';

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

export default function StudentDashboard({ user, navigate }: Props) {
  const [events, setEvents]       = useState<ApiEvent[]>([]);
  const [regs, setRegs]           = useState<ApiRegistration[]>([]);
  const [notifs, setNotifs]       = useState<ApiNotification[]>([]);
  const [certCount, setCertCount] = useState(0);
  const [loading, setLoading]     = useState(true);

  useEffect(() => {
    Promise.all([apiGetEvents(), apiGetMyRegistrations(), apiGetMyCertificates(), apiGetMyNotifications()])
      .then(([evs, rs, cs, ns]) => {
        setEvents(evs);
        setRegs(rs);
        setCertCount(cs.length);
        setNotifs(ns.filter(n => !(n.isRead === true || (n as any).read === true)));
      })
      .catch(() => { /* silent — show empty state */ })
      .finally(() => setLoading(false));

    const handleUpdate = () => {
      apiGetMyNotifications()
        .then(ns => setNotifs(ns.filter(n => !(n.isRead === true || (n as any).read === true))))
        .catch(() => {});
    };
    window.addEventListener('notifications-updated', handleUpdate);
    window.addEventListener('notification-refresh', handleUpdate);
    return () => {
      window.removeEventListener('notifications-updated', handleUpdate);
      window.removeEventListener('notification-refresh', handleUpdate);
    };
  }, []);

  const upcoming   = events.filter(e => e.status === 'PUBLISHED').slice(0, 3);
  const regCount   = regs.filter(r => r.status === 'REGISTERED').length;
  const waitCount  = regs.filter(r => r.status === 'WAITLISTED').length;

  const goToEvent  = (id: number) => navigate('event-detail', { selectedEventId: String(id) });

  return (
    <>
      {/* Welcome bar */}
      <div className="welcome-bar">
        <div className="welcome-inner">
          <div>
            <h1 className="welcome-title">
              Welcome back, {user.name.split(' ')[0]}! 👋
            </h1>
            <p className="welcome-sub">
              {user.registerNumber} &nbsp;·&nbsp; {user.department} &nbsp;·&nbsp; {user.year}
            </p>
          </div>
          <div className="welcome-pill">
            🗓️ {new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: 0 }}>
        {/* Deadline Reminders Banner */}
        {notifs.length > 0 && (
          <div
            style={{
              background: 'linear-gradient(135deg, #fef3c7 0%, #fffbeb 100%)',
              border: '1px solid #fde68a',
              borderRadius: '12px',
              padding: '1rem 1.25rem',
              marginBottom: '1.5rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 2px 6px rgba(217, 119, 6, 0.08)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{ fontSize: '1.75rem' }}>⏰</div>
              <div>
                <div style={{ fontWeight: 700, color: '#92400e', fontSize: '0.92rem' }}>
                  Registration Closing Tomorrow!
                </div>
                <div style={{ color: '#b45309', fontSize: '0.82rem', marginTop: '2px' }}>
                  {notifs[0].message}
                </div>
              </div>
            </div>
            <button
              className="btn btn-navy btn-sm"
              onClick={() => goToEvent(notifs[0].eventId)}
              style={{ whiteSpace: 'nowrap', marginLeft: '12px' }}
            >
              View Event →
            </button>
          </div>
        )}

        {/* Stats */}
        <div className="stats-grid" style={{ marginBottom: '1.75rem' }}>
          <div className="stat-card" style={{ cursor: 'pointer' }} onClick={() => navigate('events')}>
            <div className="stat-icon icon-blue">🎪</div>
            <div>
              <div className="stat-val">{events.filter(e => e.status === 'PUBLISHED').length}</div>
              <div className="stat-lbl">Upcoming Events</div>
            </div>
          </div>
          <div className="stat-card" style={{ cursor: 'pointer' }} onClick={() => navigate('my-registrations')}>
            <div className="stat-icon icon-green">✅</div>
            <div>
              <div className="stat-val">{regCount}</div>
              <div className="stat-lbl">Registered</div>
            </div>
          </div>
          <div className="stat-card" style={{ cursor: 'pointer' }} onClick={() => navigate('my-registrations')}>
            <div className="stat-icon icon-orange">⏳</div>
            <div>
              <div className="stat-val">{waitCount}</div>
              <div className="stat-lbl">Waitlisted</div>
            </div>
          </div>
          <div className="stat-card" style={{ cursor: 'pointer' }} onClick={() => navigate('my-certificates')}>
            <div className="stat-icon icon-purple">🏆</div>
            <div>
              <div className="stat-val">{certCount}</div>
              <div className="stat-lbl">Certificates</div>
            </div>
          </div>
        </div>

        {/* Upcoming events section */}
        <div className="section-header">
          <h2 className="section-title">🎪 Upcoming Events</h2>
          <button className="btn btn-outline btn-sm" onClick={() => navigate('events')}>
            View All →
          </button>
        </div>

        {loading ? (
          <div style={{ color: '#64748b', padding: '1rem 0' }}>⏳ Loading events…</div>
        ) : upcoming.length === 0 ? (
          <div style={{ color: '#94a3b8', padding: '1rem 0' }}>No published events at the moment.</div>
        ) : (
          <div className="events-grid" style={{ marginBottom: '2rem' }}>
            {upcoming.map(ev => (
              <div
                key={ev.id}
                className="event-card"
                onClick={() => goToEvent(ev.id)}
                style={{ cursor: 'pointer' }}
              >
                <div className="event-card-header">
                  <div className="event-emoji">🎫</div>
                  <span style={{
                    background: '#d1fae5', color: '#065f46',
                    padding: '2px 10px', borderRadius: 12, fontSize: '0.72rem', fontWeight: 700
                  }}>OPEN</span>
                </div>
                <h3 className="event-title">{ev.title}</h3>
                <div className="event-meta">
                  <span>📅 {fmtDate(ev.eventDate)}</span>
                  <span>📍 {ev.venue}</span>
                  <span>💺 {ev.totalSeats - ev.registeredCount} seats left</span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Recent registrations */}
        {regs.length > 0 && (
          <>
            <div className="section-header">
              <h2 className="section-title">📋 Recent Registrations</h2>
              <button className="btn btn-outline btn-sm" onClick={() => navigate('my-registrations')}>
                View All →
              </button>
            </div>
            <div className="card" style={{ overflow: 'hidden', marginBottom: '2rem' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                    {['Event', 'Date', 'Status'].map(h => (
                      <th key={h} style={{ padding: '0.75rem 1rem', textAlign: 'left', fontSize: '0.78rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase' }}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {regs.slice(0, 5).map(r => (
                    <tr key={r.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={{ padding: '0.75rem 1rem', fontWeight: 600, color: '#1e293b' }}>
                        {r.eventTitle ?? `Event #${r.eventId}`}
                      </td>
                      <td style={{ padding: '0.75rem 1rem', color: '#475569', fontSize: '0.85rem' }}>
                        {r.eventDate ? fmtDate(r.eventDate) : '—'}
                      </td>
                      <td style={{ padding: '0.75rem 1rem' }}>
                        <span style={{
                          background: r.status === 'REGISTERED' ? '#d1fae5' : r.status === 'WAITLISTED' ? '#fef3c7' : '#fee2e2',
                          color: r.status === 'REGISTERED' ? '#065f46' : r.status === 'WAITLISTED' ? '#92400e' : '#991b1b',
                          padding: '2px 8px', borderRadius: 10, fontSize: '0.75rem', fontWeight: 700,
                        }}>
                          {r.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </>
  );
}
