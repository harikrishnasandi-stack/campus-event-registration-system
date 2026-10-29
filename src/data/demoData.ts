import type { CampusEvent, Registration, User } from '../types';

// ─── Demo Users ───────────────────────────────────────────────────────────────
// DEMO ONLY — passwords are not hashed and auth is not real.
// Real backend will use BCrypt + JWT.

export const DEMO_USERS: User[] = [
  {
    id: 'u-admin-1',
    name: 'Campus Admin',
    email: 'admin@campus.edu',
    registerNumber: 'ADMIN001',
    department: 'Administration',
    year: 'N/A',
    role: 'ADMIN',
  },
  {
    id: 'u-student-1',
    name: 'Arjun Sharma',
    email: 'student@campus.edu',
    registerNumber: 'CS2024001',
    department: 'Computer Science',
    year: '3rd Year',
    role: 'STUDENT',
  },
  {
    id: 'u-student-2',
    name: 'Priya Nair',
    email: 'priya@campus.edu',
    registerNumber: 'EC2024042',
    department: 'Electronics & Communication',
    year: '2nd Year',
    role: 'STUDENT',
  },
];

// Demo passwords (plaintext — DEMO ONLY)
// Admin:   admin@campus.edu   / Admin@1234
// Student: student@campus.edu / Student@1234
export const DEMO_PASSWORDS: Record<string, string> = {
  'admin@campus.edu':   'Admin@1234',
  'student@campus.edu': 'Student@1234',
  'priya@campus.edu':   'Priya@1234',
};

// ─── Demo Events ──────────────────────────────────────────────────────────────

export const DEMO_EVENTS: CampusEvent[] = [
  {
    id: 'evt-1',
    title: 'Annual Tech Symposium 2024',
    category: 'Technology',
    description:
      'A full-day symposium featuring talks from industry leaders, live coding competitions, and networking sessions. Industry experts from Google, Microsoft, and TCS will share insights on AI, cloud computing, and software engineering careers.',
    venue: 'Main Auditorium, Block A',
    eventDate: getFutureDate(15),
    eventTime: '09:00',
    registrationDeadline: getFutureDate(10),
    totalSeats: 200,
    registeredCount: 142,
    status: 'UPCOMING',
    imageEmoji: '💻',
    organizer: 'CSE Department',
    instructions: [
      'Carry your college ID card at all times.',
      'Report at the venue 30 minutes before the event starts.',
      'Laptop is optional but recommended for hands-on sessions.',
      'Dress code: Smart casual.',
      'Lunch and refreshments will be provided.',
    ],
  },
  {
    id: 'evt-2',
    title: 'Web Development Workshop',
    category: 'Workshop',
    description:
      'Hands-on workshop covering React, Spring Boot, and REST APIs. Build a real-world project from scratch over two intensive days. Prerequisites: Basic knowledge of HTML, CSS, and any programming language.',
    venue: 'Computer Lab 3, Block C',
    eventDate: getFutureDate(7),
    eventTime: '10:00',
    registrationDeadline: getFutureDate(5),
    totalSeats: 50,
    registeredCount: 48,
    status: 'UPCOMING',
    imageEmoji: '🌐',
    organizer: 'IT Club',
    instructions: [
      'Bring your laptop with VS Code installed.',
      'Install Node.js 20+ and Java 17 before attending.',
      'A GitHub account is required for code submissions.',
      'Pair programming is encouraged — bring a friend!',
    ],
  },
  {
    id: 'evt-3',
    title: 'Cultural Fest 2024 — Kaleidoscope',
    category: 'Cultural',
    description:
      'Annual cultural festival with music, dance, drama, and art competitions. Open to all departments. Showcase your talent and win exciting prizes worth over ₹1,00,000. Guest performances by renowned artists.',
    venue: 'Open Air Theatre',
    eventDate: getFutureDate(30),
    eventTime: '11:00',
    registrationDeadline: getFutureDate(25),
    totalSeats: 500,
    registeredCount: 187,
    status: 'UPCOMING',
    imageEmoji: '🎭',
    organizer: 'Cultural Committee',
    instructions: [
      'Register for specific sub-events separately at the venue.',
      'Entry is free for registered participants.',
      'Guest passes available at the main gate (₹50 each).',
      'Costumes and props must be arranged by participants.',
    ],
  },
  {
    id: 'evt-4',
    title: 'Interview Preparation Bootcamp',
    category: 'Career',
    description:
      'Intensive 2-day bootcamp covering DSA, system design, and mock interviews with industry mentors. Specially designed for students appearing in campus placements. Past participants achieved 85% placement success rate.',
    venue: 'Seminar Hall 2, Block B',
    eventDate: getFutureDate(3),
    eventTime: '08:30',
    registrationDeadline: getFutureDate(2),
    totalSeats: 80,
    registeredCount: 80,
    status: 'UPCOMING',
    imageEmoji: '🎯',
    organizer: 'Training & Placement Cell',
    instructions: [
      'Pre-requisite: Basic knowledge of any programming language.',
      'Submit your resume 3 days in advance via the portal.',
      'Bring a notebook and pen for the sessions.',
      'Mock interviews will be conducted on Day 2.',
    ],
  },
  {
    id: 'evt-5',
    title: 'Environmental Awareness Drive',
    category: 'Social',
    description:
      'Campus-wide tree planting drive and awareness campaign. Join us to plant 500 saplings across campus and learn about sustainability practices. Certificates will be awarded to all participants.',
    venue: 'Campus Grounds (Assembly at Gate 1)',
    eventDate: getFutureDate(21),
    eventTime: '07:00',
    registrationDeadline: getFutureDate(18),
    totalSeats: 300,
    registeredCount: 64,
    status: 'UPCOMING',
    imageEmoji: '🌱',
    organizer: 'NSS Unit',
    instructions: [
      'Wear comfortable clothing and shoes suitable for outdoor activity.',
      'Bring a water bottle.',
      'Gloves and gardening tools will be provided.',
      'Attendance will be marked for NCC/NSS credit.',
    ],
  },
  {
    id: 'evt-6',
    title: 'Freshers Welcome Orientation',
    category: 'Academic',
    description:
      'Official welcome ceremony for the 2024 batch of first-year students. Meet faculty, seniors, and learn about campus clubs, facilities, and academic expectations.',
    venue: 'Main Auditorium, Block A',
    eventDate: getFutureDate(-5),
    eventTime: '10:00',
    registrationDeadline: getFutureDate(-7),
    totalSeats: 400,
    registeredCount: 372,
    status: 'COMPLETED',
    imageEmoji: '🎓',
    organizer: 'Dean, Student Affairs',
    instructions: [
      'Mandatory for all 1st year students.',
      'Bring your original admission documents.',
      'Parents are welcome to attend.',
    ],
  },
];

// ─── Demo Registrations ───────────────────────────────────────────────────────

export const DEMO_REGISTRATIONS: Registration[] = [
  {
    id: 'reg-1',
    eventId: 'evt-1',
    studentId: 'u-student-1',
    status: 'REGISTERED',
    registeredAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
    eventTitle: 'Annual Tech Symposium 2024',
    eventDate: getFutureDate(15),
    eventTime: '09:00',
    venue: 'Main Auditorium, Block A',
  },
  {
    id: 'reg-2',
    eventId: 'evt-4',
    studentId: 'u-student-1',
    status: 'WAITLISTED',
    registeredAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
    eventTitle: 'Interview Preparation Bootcamp',
    eventDate: getFutureDate(3),
    eventTime: '08:30',
    venue: 'Seminar Hall 2, Block B',
  },
  {
    id: 'reg-3',
    eventId: 'evt-6',
    studentId: 'u-student-1',
    status: 'REGISTERED',
    registeredAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
    eventTitle: 'Freshers Welcome Orientation',
    eventDate: getFutureDate(-5),
    eventTime: '10:00',
    venue: 'Main Auditorium, Block A',
  },
];

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getFutureDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString().split('T')[0];
}
