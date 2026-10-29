// ─── Domain Types ────────────────────────────────────────────────────────────

export type Role = 'STUDENT' | 'ADMIN';

export type EventStatus = 'UPCOMING' | 'ONGOING' | 'COMPLETED' | 'CANCELLED';

export type RegistrationStatus = 'REGISTERED' | 'WAITLISTED' | 'CANCELLED';

export interface User {
  id: string;
  name: string;
  email: string;
  registerNumber?: string;
  department?: string;
  year?: string;
  role: Role;
  /** JWT token – will be set by real backend; demo leaves it empty */
  token?: string;
}

export interface CampusEvent {
  id: string;
  title: string;
  category: string;
  description: string;
  venue: string;
  eventDate: string;       // ISO date string
  eventTime: string;       // "HH:MM"
  registrationDeadline: string;
  totalSeats: number;
  registeredCount: number;
  status: EventStatus;
  instructions: string[];
  organizer: string;
  imageEmoji: string;      // quick visual placeholder
}

export interface Registration {
  id: string;
  eventId: string;
  studentId: string;
  status: RegistrationStatus;
  registeredAt: string;
  eventTitle: string;
  eventDate: string;
  eventTime: string;
  venue: string;
}

// ─── Auth / App State ─────────────────────────────────────────────────────────

export type Page =
  | 'login'
  | 'student-dashboard'
  | 'events'
  | 'event-detail'
  | 'my-registrations'
  | 'my-certificates'
  | 'admin-dashboard'
  | 'admin-events'
  | 'admin-participants'
  | 'admin-certificates';

export interface AppState {
  page: Page;
  user: User | null;
  selectedEventId: string | null;
}

