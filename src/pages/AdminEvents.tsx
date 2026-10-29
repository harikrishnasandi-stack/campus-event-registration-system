import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import {
  apiGetAdminEvents, apiCreateEvent, apiUpdateEvent, apiUpdateEventStatus, apiCancelEvent,
  type ApiEvent, type CreateEventPayload,
} from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function toDatetimeLocal(iso: string) {
  if (!iso) return '';
  return new Date(iso).toISOString().slice(0, 16);
}

const EMPTY_FORM: CreateEventPayload = {
  title: '', description: '', instructions: '', venue: '',
  eventDate: '', registrationDeadline: '', totalSeats: 100,
};

export default function AdminEvents({ navigate }: Props) {
  const [events, setEvents]     = useState<ApiEvent[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm]         = useState<CreateEventPayload>(EMPTY_FORM);
  const [saving, setSaving]     = useState(false);
  const [saveError, setSaveError] = useState('');
  const [statusMsg, setStatusMsg] = useState('');

  const load = () => {
    setLoading(true);
    apiGetAdminEvents()
      .then(setEvents)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setSaveError('');
    setShowModal(true);
  };

  const openEdit = (ev: ApiEvent) => {
    setForm({
      title: ev.title,
      description: ev.description ?? '',
      instructions: ev.instructions ?? '',
      venue: ev.venue ?? '',
      eventDate: toDatetimeLocal(ev.eventDate),
      registrationDeadline: toDatetimeLocal(ev.registrationDeadline),
      totalSeats: ev.totalSeats,
    });
    setEditingId(ev.id);
    setSaveError('');
    setShowModal(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveError('');
    try {
      if (editingId) {
        const updated = await apiUpdateEvent(editingId, form);
        setEvents(evs => evs.map(e => e.id === editingId ? updated : e));
      } else {
        const created = await apiCreateEvent(form);
        setEvents(evs => [created, ...evs]);
      }
      setShowModal(false);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Save failed.');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (ev: ApiEvent, newStatus: string) => {
    setStatusMsg('');
    try {
      if (newStatus === 'CANCELLED') {
        await apiCancelEvent(ev.id);
        setStatusMsg(`✅ "${ev.title}" cancelled successfully`);
      } else {
        await apiUpdateEventStatus(ev.id, newStatus);
        setStatusMsg(`✅ "${ev.title}" is now ${newStatus}`);
      }
      load(); // refresh the event list after status change
      setTimeout(() => setStatusMsg(''), 4000);
    } catch (err) {
      setStatusMsg(`⚠️ ${err instanceof Error ? err.message : 'Failed to update status'}`);
    }
  };

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <h1 className="page-title">🎪 Manage Events</h1>
            <p className="page-subtitle">{events.length} total events</p>
          </div>
          <button className="btn btn-navy" onClick={openCreate}>+ Create Event</button>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {statusMsg && (
          <div className={`alert ${statusMsg.startsWith('⚠️') ? 'alert-error' : 'alert-success'}`} style={{ marginBottom: '1rem' }}>
            {statusMsg}
          </div>
        )}

        {loading && <div style={{ color: '#64748b', padding: '2rem 0' }}>⏳ Loading events…</div>}
        {error && <div className="alert alert-error">⚠️ {error}</div>}

        {!loading && !error && (
          <div className="card" style={{ overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 900 }}>
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
                    <td style={{ padding: '0.85rem 1rem', fontWeight: 600, color: '#1e293b', maxWidth: 220 }}>
                      {ev.title}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                      {fmtDate(ev.eventDate)}
                    </td>
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
                      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                        <button className="btn btn-outline btn-sm" onClick={() => openEdit(ev)}>✏️ Edit</button>
                        <button className="btn btn-outline btn-sm" onClick={() => navigate('admin-participants', { selectedEventId: String(ev.id) })}>
                          👥 Participants
                        </button>
                        {ev.status === 'DRAFT' && (
                          <button className="btn btn-sm" style={{ background: '#16a34a', color: '#fff', border: 'none', cursor: 'pointer', borderRadius: 6, padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                            onClick={() => changeStatus(ev, 'PUBLISHED')}>
                            📢 Publish
                          </button>
                        )}
                        {ev.status === 'PUBLISHED' && (
                          <button className="btn btn-sm" style={{ background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer', borderRadius: 6, padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                            onClick={() => changeStatus(ev, 'COMPLETED')}>
                            ✅ Complete
                          </button>
                        )}
                        {(ev.status === 'PUBLISHED' || ev.status === 'DRAFT') && (
                          <button className="btn btn-sm" style={{ background: '#dc2626', color: '#fff', border: 'none', cursor: 'pointer', borderRadius: 6, padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                            onClick={() => changeStatus(ev, 'CANCELLED')}>
                            ❌ Cancel
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

      {/* ── Create / Edit Modal ─────────────────────────────── */}
      {showModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem',
        }} onClick={() => setShowModal(false)}>
          <div style={{
            background: '#fff', borderRadius: 12, padding: '2rem', width: '100%', maxWidth: 600,
            maxHeight: '90vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h2 style={{ marginBottom: '1.5rem', color: '#1e3a8a' }}>
              {editingId ? '✏️ Edit Event' : '🎪 Create New Event'}
            </h2>

            {saveError && <div className="alert alert-error" style={{ marginBottom: '1rem' }}>⚠️ {saveError}</div>}

            <form onSubmit={handleSave}>
              <div className="form-group">
                <label className="form-label">Event Title *</label>
                <input type="text" className="form-control" required
                  value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                  placeholder="e.g. Annual Tech Symposium 2024" />
              </div>
              <div className="form-group">
                <label className="form-label">Description *</label>
                <textarea className="form-control" required rows={3}
                  value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  placeholder="Describe the event…" />
              </div>
              <div className="form-group">
                <label className="form-label">Venue *</label>
                <input type="text" className="form-control" required
                  value={form.venue} onChange={e => setForm(f => ({ ...f, venue: e.target.value }))}
                  placeholder="e.g. Main Auditorium, Block A" />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Event Date *</label>
                  <input type="datetime-local" className="form-control" required
                    value={form.eventDate} onChange={e => setForm(f => ({ ...f, eventDate: e.target.value }))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Registration Deadline *</label>
                  <input type="datetime-local" className="form-control" required
                    value={form.registrationDeadline} onChange={e => setForm(f => ({ ...f, registrationDeadline: e.target.value }))} />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Total Seats *</label>
                <input type="number" className="form-control" required min={1}
                  value={form.totalSeats} onChange={e => setForm(f => ({ ...f, totalSeats: Number(e.target.value) }))} />
              </div>
              <div className="form-group">
                <label className="form-label">Instructions (one per line)</label>
                <textarea className="form-control" rows={4}
                  value={form.instructions} onChange={e => setForm(f => ({ ...f, instructions: e.target.value }))}
                  placeholder="1. Bring your college ID&#10;2. Report 30 minutes before&#10;3. Dress code: Smart casual" />
              </div>
              <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-outline" onClick={() => setShowModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-navy" disabled={saving}>
                  {saving ? '⏳ Saving…' : editingId ? '💾 Save Changes' : '🎪 Create Event'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
