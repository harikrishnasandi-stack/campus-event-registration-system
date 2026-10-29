// =============================================================
// API Client — all backend calls go through here.
// Base URL is proxied via Vite (/api -> http://localhost:8080/api)
// JWT token is read from localStorage on every request.
// =============================================================

const BASE = '/api';

// ── helpers ──────────────────────────────────────────────────

function getToken(): string | null {
  return localStorage.getItem('jwt_token');
}

function authHeaders(): HeadersInit {
  const token = getToken();
  return token
    ? { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
    : { 'Content-Type': 'application/json' };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      msg = body.error || body.message || msg;
    } catch { /* ignore */ }
    throw new Error(msg);
  }
  // 204 No Content
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// ── Auth ─────────────────────────────────────────────────────

export interface LoginResponse {
  token: string;
  userId?: number;
  id?: number;
  name: string;
  email: string;
  registerNumber: string;
  department: string;
  year: string;
  role: 'STUDENT' | 'ADMIN';
}

export async function apiLogin(email: string, password: string): Promise<LoginResponse> {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  return handleResponse<LoginResponse>(res);
}

export interface RegisterPayload {
  registerNumber: string;
  name: string;
  email: string;
  password: string;
  department: string;
  year: string;
}

export async function apiRegister(payload: RegisterPayload): Promise<LoginResponse> {
  const res = await fetch(`${BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return handleResponse<LoginResponse>(res);
}

// ── Events ───────────────────────────────────────────────────

export interface ApiEvent {
  id: number;
  title: string;
  description: string;
  instructions: string;
  venue: string;
  eventDate: string;         // ISO datetime
  registrationDeadline: string;
  totalSeats: number;
  registeredCount: number;
  status: 'DRAFT' | 'PUBLISHED' | 'COMPLETED' | 'CANCELLED';
  createdBy: number;
  createdAt: string;
}

export async function apiGetEvents(): Promise<ApiEvent[]> {
  const res = await fetch(`${BASE}/events`, { headers: authHeaders() });
  return handleResponse<ApiEvent[]>(res);
}

export async function apiGetEventById(id: number): Promise<ApiEvent> {
  const res = await fetch(`${BASE}/events/${id}`, { headers: authHeaders() });
  return handleResponse<ApiEvent>(res);
}

export async function apiGetAdminEvents(): Promise<ApiEvent[]> {
  const res = await fetch(`${BASE}/events/admin/all`, { headers: authHeaders() });
  return handleResponse<ApiEvent[]>(res);
}

export interface CreateEventPayload {
  title: string;
  description: string;
  instructions: string;
  venue: string;
  eventDate: string;
  registrationDeadline: string;
  totalSeats: number;
}

export async function apiCreateEvent(payload: CreateEventPayload): Promise<ApiEvent> {
  const res = await fetch(`${BASE}/events`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(payload),
  });
  return handleResponse<ApiEvent>(res);
}

export async function apiUpdateEvent(id: number, payload: Partial<CreateEventPayload>): Promise<ApiEvent> {
  const res = await fetch(`${BASE}/events/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(payload),
  });
  return handleResponse<ApiEvent>(res);
}

export async function apiUpdateEventStatus(id: number, status: string): Promise<ApiEvent> {
  const res = await fetch(`${BASE}/events/${id}/status`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ status }),
  });
  return handleResponse<ApiEvent>(res);
}

export async function apiCancelEvent(id: number): Promise<{ message: string; event: ApiEvent }> {
  const res = await fetch(`${BASE}/events/${id}/cancel`, {
    method: 'PUT',
    headers: authHeaders(),
  });
  return handleResponse<{ message: string; event: ApiEvent }>(res);
}

// ── Registrations ─────────────────────────────────────────────

export interface ApiRegistration {
  id: number;
  studentId: number;
  eventId: number;
  status: 'REGISTERED' | 'WAITLISTED' | 'CANCELLED';
  registeredAt: string;
  // enriched fields
  eventTitle?: string;
  eventDate?: string;
  venue?: string;
  studentName?: string;
  registerNumber?: string;
}

export async function apiRegisterForEvent(eventId: number): Promise<ApiRegistration> {
  const res = await fetch(`${BASE}/registrations/event/${eventId}`, {
    method: 'POST',
    headers: authHeaders(),
  });
  return handleResponse<ApiRegistration>(res);
}

export async function apiGetMyRegistrations(): Promise<ApiRegistration[]> {
  const res = await fetch(`${BASE}/registrations/my`, { headers: authHeaders() });
  return handleResponse<ApiRegistration[]>(res);
}

export async function apiGetEventParticipants(eventId: number): Promise<ApiRegistration[]> {
  const res = await fetch(`${BASE}/registrations/event/${eventId}`, { headers: authHeaders() });
  return handleResponse<ApiRegistration[]>(res);
}

// ── Certificates ──────────────────────────────────────────────

export interface ApiCertificate {
  id: number;
  studentId: number;
  eventId: number;
  fileName: string;
  storedFileName: string;
  fileSize: number;
  uploadedAt: string;
  uploadedBy: number;
  // enriched
  studentName?: string;
  registerNumber?: string;
  eventTitle?: string;
}

export async function apiGetMyCertificates(): Promise<ApiCertificate[]> {
  const res = await fetch(`${BASE}/certificates/my`, { headers: authHeaders() });
  return handleResponse<ApiCertificate[]>(res);
}

export async function apiGetEventCertificates(eventId: number): Promise<ApiCertificate[]> {
  const res = await fetch(`${BASE}/certificates/event/${eventId}`, { headers: authHeaders() });
  return handleResponse<ApiCertificate[]>(res);
}

export async function apiGetAllCertificates(): Promise<ApiCertificate[]> {
  const res = await fetch(`${BASE}/certificates/all`, { headers: authHeaders() });
  return handleResponse<ApiCertificate[]>(res);
}

export interface UploadCertificateResponse {
  message: string;
  fileName: string;
  fileSize: number;
  studentId: number;
  eventId: number;
}

export async function apiUploadCertificate(
  studentId: number,
  eventId: number,
  file: File
): Promise<UploadCertificateResponse> {
  const token = getToken();
  const form = new FormData();
  form.append('file', file);
  form.append('studentId', String(studentId));
  form.append('eventId', String(eventId));
  const res = await fetch(`${BASE}/certificates/upload`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  return handleResponse<UploadCertificateResponse>(res);
}

/** Build a URL to download a certificate by studentId + eventId (query params). */
export function getCertificateDownloadUrl(studentId: number, eventId: number): string {
  return `${BASE}/certificates/download?studentId=${studentId}&eventId=${eventId}`;
}

// ── Students (admin) ──────────────────────────────────────────

export interface ApiStudent {
  id: number;
  registerNumber: string;
  name: string;
  email: string;
  department: string;
  year: string;
  role: 'STUDENT' | 'ADMIN';
  createdAt: string;
}

export async function apiGetAllStudents(): Promise<ApiStudent[]> {
  const res = await fetch(`${BASE}/students`, { headers: authHeaders() });
  return handleResponse<ApiStudent[]>(res);
}

// ── Notifications ─────────────────────────────────────────────

export interface ApiNotification {
  id: number;
  studentId: number;
  eventId: number;
  notificationType: string;
  message: string;
  isRead: boolean;
  read?: boolean;
  createdAt: string;
  eventTitle?: string;
  registrationDeadline?: string;
}

export async function apiGetMyNotifications(): Promise<ApiNotification[]> {
  const res = await fetch(`${BASE}/notifications`, { headers: authHeaders() });
  return handleResponse<ApiNotification[]>(res);
}

export async function apiGetUnreadNotificationCount(): Promise<{ unreadCount: number }> {
  const res = await fetch(`${BASE}/notifications/unread-count`, { headers: authHeaders() });
  return handleResponse<{ unreadCount: number }>(res);
}

export interface MarkNotificationReadResponse {
  message: string;
  id?: number;
  wasUnread?: boolean;
  unreadCount?: number;
}

export async function apiMarkNotificationAsRead(id: number): Promise<MarkNotificationReadResponse> {
  const res = await fetch(`${BASE}/notifications/${id}/read`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  return handleResponse<MarkNotificationReadResponse>(res);
}

export interface MarkAllReadResponse {
  message: string;
  updatedCount: number;
  unreadCount: number;
}

export async function apiMarkAllNotificationsAsRead(): Promise<MarkAllReadResponse> {
  const res = await fetch(`${BASE}/notifications/read-all`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  return handleResponse<MarkAllReadResponse>(res);
}
