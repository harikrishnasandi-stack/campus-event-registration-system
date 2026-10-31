import type { CampusEvent } from '../types';

interface Props {
  event: CampusEvent;
  onViewDetail: (id: string) => void;
}

export function seatPercent(ev: CampusEvent) {
  return ev.totalSeats > 0 ? Math.round((ev.registeredCount / ev.totalSeats) * 100) : 0;
}

export function seatColor(pct: number) {
  if (pct >= 95) return '#dc2626';
  if (pct >= 80) return '#d97706';
  return '#059669';
}

export function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', {
    weekday: 'short', day: '2-digit', month: 'short', year: 'numeric',
  });
}

export function fmtDateTime(iso: string, time: string) {
  return `${fmtDate(iso)} at ${time}`;
}

export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    UPCOMING:   'badge-primary',
    ONGOING:    'badge-success',
    COMPLETED:  'badge-info',
    CANCELLED:  'badge-danger',
    REGISTERED: 'badge-success',
    WAITLISTED: 'badge-warning',
    PUBLISHED:  'badge-success',
    DRAFT:      'badge-neutral',
  };
  return <span className={`badge ${map[status] ?? 'badge-neutral'}`}>{status}</span>;
}

export function CategoryBadge({ cat }: { cat: string }) {
  const map: Record<string, string> = {
    Technology: 'badge-navy',
    Workshop:   'badge-purple',
    Cultural:   'badge-warning',
    Career:     'badge-success',
    Social:     'badge-info',
    Academic:   'badge-primary',
  };
  return <span className={`badge ${map[cat] ?? 'badge-neutral'}`}>{cat}</span>;
}

export default function EventCard({ event, onViewDetail }: Props) {
  const pct = seatPercent(event);
  const now = new Date().toISOString().split('T')[0];
  const deadlinePast = event.registrationDeadline < now;
  const isFull = event.registeredCount >= event.totalSeats;

  return (
    <article className="event-card">
      {/* Banner */}
      <div className="event-card-banner">
        <div className="event-emoji-large">{event.imageEmoji}</div>
        <h3 className="event-card-title">{event.title}</h3>
        <div className="event-card-cat">{event.category}</div>
      </div>

      {/* Body */}
      <div className="event-card-body">
        <p className="event-card-desc">{event.description}</p>

        <div className="event-meta">
          <div className="meta-row">📍 <strong>{event.venue}</strong></div>
          <div className="meta-row">📅 <strong>{fmtDateTime(event.eventDate, event.eventTime)}</strong></div>
          <div className="meta-row">⏰ Deadline: <strong>{fmtDate(event.registrationDeadline)}</strong></div>
          <div className="meta-row" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: 4 }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>
              {event.registeredCount}/{event.totalSeats} seats
              {isFull && <span style={{ color: 'var(--danger)', marginLeft: 6, fontWeight: 700 }}>· FULL</span>}
            </span>
            <div className="seat-track" style={{ width: '100%' }}>
              <div className="seat-fill" style={{ width: `${pct}%`, background: seatColor(pct) }} />
            </div>
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="event-card-footer">
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          <StatusBadge status={event.status} />
          {event.fee !== undefined && (
            <span style={{
              background: event.fee > 0 ? '#ecfdf5' : '#f1f5f9',
              color: event.fee > 0 ? '#047857' : '#475569',
              border: event.fee > 0 ? '1px solid #a7f3d0' : '1px solid #cbd5e1',
              padding: '1px 8px', borderRadius: 10, fontSize: '0.75rem', fontWeight: 700
            }}>
              {event.fee > 0 ? `₹${event.fee}` : 'FREE'}
            </span>
          )}
          {deadlinePast && <span className="badge badge-danger">Closed</span>}
          {isFull && !deadlinePast && <span className="badge badge-warning">Waitlist</span>}
        </div>
        <button
          className="btn btn-primary btn-sm"
          onClick={() => onViewDetail(event.id)}
        >
          View Details →
        </button>
      </div>
    </article>
  );
}

