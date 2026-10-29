import { useState, useEffect, useMemo } from 'react';
import type { User, Page } from '../types';
import { apiGetEventParticipants, apiGetAdminEvents, type ApiRegistration, type ApiEvent } from '../api/client';

interface Props {
  eventId: string;
  user: User;
  navigate: (page: Page) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function AdminParticipants({ eventId, navigate }: Props) {
  const id = Number(eventId);
  const [participants, setParticipants] = useState<ApiRegistration[]>([]);
  const [event, setEvent]               = useState<ApiEvent | null>(null);
  const [loading, setLoading]           = useState(true);
  const [error, setError]               = useState('');
  const [search, setSearch]             = useState('');

  useEffect(() => {
    Promise.all([
      apiGetEventParticipants(id),
      apiGetAdminEvents(),
    ])
      .then(([parts, events]) => {
        setParticipants(parts as ApiRegistration[]);
        const found = (events as ApiEvent[]).find(e => e.id === id);
        if (found) setEvent(found);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  const filtered = useMemo(() => {
    if (!search.trim()) return participants;
    const q = search.toLowerCase();
    return participants.filter(p =>
      (p.studentName ?? '').toLowerCase().includes(q) ||
      (p.registerNumber ?? '').toLowerCase().includes(q)
    );
  }, [participants, search]);

  const registeredCount = participants.filter(p => p.status === 'REGISTERED').length;
  const waitlistCount   = participants.filter(p => p.status === 'WAITLISTED').length;

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <button className="btn btn-outline btn-sm" onClick={() => navigate('admin-events')} style={{ marginBottom: '0.5rem' }}>
              ← Back to Events
            </button>
            <h1 className="page-title">👥 Participants</h1>
            <p className="page-subtitle">
              {event ? event.title : `Event #${id}`}
              &nbsp;·&nbsp; {registeredCount} registered, {waitlistCount} waitlisted
            </p>
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {/* Search */}
        <div className="search-wrap" style={{ marginBottom: '1.5rem' }}>
          <input
            type="text" className="search-input"
            placeholder="🔍 Search by name or register number…"
            value={search} onChange={e => setSearch(e.target.value)}
          />
        </div>

        {loading && <div style={{ color: '#64748b', padding: '2rem 0' }}>⏳ Loading participants…</div>}
        {error && <div className="alert alert-error">⚠️ {error}</div>}

        {!loading && !error && participants.length === 0 && (
          <div style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
            No participants registered for this event yet.
          </div>
        )}

        {!loading && !error && participants.length > 0 && (
          <div className="card" style={{ overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                  {['#', 'Name', 'Register Number', 'Registered On', 'Status'].map(h => (
                    <th key={h} style={{ padding: '0.85rem 1rem', textAlign: 'left', fontSize: '0.78rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((p, i) => (
                  <tr key={p.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.85rem 1rem', color: '#94a3b8', fontSize: '0.85rem' }}>{i + 1}</td>
                    <td style={{ padding: '0.85rem 1rem', fontWeight: 600, color: '#1e293b' }}>
                      {p.studentName ?? `Student #${p.studentId}`}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontFamily: 'monospace', fontSize: '0.875rem' }}>
                      {p.registerNumber ?? '—'}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem' }}>
                      {fmtDate(p.registeredAt)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <span style={{
                        background: p.status === 'REGISTERED' ? '#d1fae5' : p.status === 'WAITLISTED' ? '#fef3c7' : '#fee2e2',
                        color: p.status === 'REGISTERED' ? '#065f46' : p.status === 'WAITLISTED' ? '#92400e' : '#991b1b',
                        padding: '2px 10px', borderRadius: 12, fontSize: '0.75rem', fontWeight: 700,
                      }}>{p.status}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filtered.length === 0 && (
              <div style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8' }}>
                No participants match your search.
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}
