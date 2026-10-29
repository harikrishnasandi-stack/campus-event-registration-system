import { useState, useEffect, useMemo } from 'react';
import type { User, Page } from '../types';
import { apiGetEvents, type ApiEvent } from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

const STATUSES = ['All', 'PUBLISHED', 'COMPLETED', 'CANCELLED'];

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function seatsLeft(ev: ApiEvent) {
  return ev.totalSeats - ev.registeredCount;
}

function statusColor(status: string) {
  switch (status) {
    case 'PUBLISHED':  return '#16a34a';
    case 'COMPLETED':  return '#6366f1';
    case 'CANCELLED':  return '#dc2626';
    default:           return '#64748b';
  }
}

export default function EventsPage({ navigate }: Props) {
  const [events, setEvents]     = useState<ApiEvent[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [search, setSearch]     = useState('');
  const [status, setStatus]     = useState('All');

  useEffect(() => {
    apiGetEvents()
      .then(setEvents)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const goToEvent = (id: number) => navigate('event-detail', { selectedEventId: String(id) });

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return events.filter(ev => {
      const matchSearch = !q ||
        ev.title.toLowerCase().includes(q) ||
        ev.venue.toLowerCase().includes(q) ||
        ev.description.toLowerCase().includes(q);
      const matchStatus = status === 'All' || ev.status === status;
      return matchSearch && matchStatus;
    });
  }, [events, search, status]);

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <h1 className="page-title">🎪 Browse Events</h1>
            <p className="page-subtitle">
              {loading ? 'Loading…' : `${filtered.length} event${filtered.length !== 1 ? 's' : ''} found`}
              &nbsp;·&nbsp; Sorted by date (Merge Sort — O(n log n) in EventSorter.java)
            </p>
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {/* Filters */}
        <div className="search-wrap">
          <input
            type="text" className="search-input"
            placeholder="🔍 Search by title, venue, or description…"
            value={search} onChange={e => setSearch(e.target.value)}
          />
          <select className="form-control" style={{ width: 'auto', minWidth: 140 }}
            value={status} onChange={e => setStatus(e.target.value)}>
            {STATUSES.map(s => <option key={s}>{s}</option>)}
          </select>
        </div>

        {/* States */}
        {loading && (
          <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
            ⏳ Loading events from server…
          </div>
        )}
        {error && (
          <div className="alert alert-error">⚠️ {error}</div>
        )}

        {/* Events grid */}
        {!loading && !error && (
          <>
            {filtered.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
                No events match your search.
              </div>
            ) : (
              <div className="events-grid">
                {filtered.map(ev => (
                  <div
                    key={ev.id}
                    className="event-card"
                    onClick={() => ev.status === 'PUBLISHED' && goToEvent(ev.id)}
                    style={{ cursor: ev.status === 'PUBLISHED' ? 'pointer' : 'default' }}
                  >
                    <div className="event-card-header">
                      <div className="event-emoji">🎫</div>
                      <span
                        className="event-status-badge"
                        style={{ background: statusColor(ev.status), color: '#fff', padding: '2px 10px', borderRadius: 12, fontSize: '0.72rem', fontWeight: 700 }}
                      >
                        {ev.status}
                      </span>
                    </div>
                    <h3 className="event-title">{ev.title}</h3>
                    <p className="event-desc" style={{ WebkitLineClamp: 2 }}>{ev.description}</p>
                    <div className="event-meta">
                      <span>📅 {fmtDate(ev.eventDate)}</span>
                      <span>📍 {ev.venue}</span>
                      <span>💺 {seatsLeft(ev)} seats left</span>
                    </div>
                    {ev.status === 'PUBLISHED' && (
                      <button
                        className="btn btn-navy"
                        style={{ marginTop: '0.75rem', width: '100%' }}
                        onClick={e => { e.stopPropagation(); goToEvent(ev.id); }}
                      >
                        View Details →
                      </button>
                    )}
                    {ev.status === 'COMPLETED' && (
                      <div style={{ marginTop: '0.75rem', textAlign: 'center', color: '#6366f1', fontSize: '0.85rem', fontWeight: 600 }}>
                        ✅ Event Completed
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
}
