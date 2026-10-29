# Campus Event Management System

A full-stack college mini-project.  
**Tech stack:** React 19 + Vite + TypeScript · Spring Boot 3 · MySQL 8 · JDBC (no JPA) · JWT

---

## ✅ Milestone 1 — Frontend Demo (COMPLETE)

### How to run the frontend right now

```powershell
# Navigate to the project
cd C:\Users\harik\CampusEventManagement

# Start dev server (Node.js path already set if using the same session)
C:\tools\node-v20.19.0-win-x64\npm.cmd run dev
```

Then open **http://localhost:5174/** in your browser.

### Demo Credentials

| Role    | Email                  | Password       |
|---------|------------------------|----------------|
| Admin   | admin@campus.edu       | Admin@1234     |
| Student | student@campus.edu     | Student@1234   |

Click **"Quick Demo Login"** buttons on the login page to auto-fill.

---

## 🗂️ Project Structure

```
CampusEventManagement/          ← Frontend (React + Vite + TypeScript)
  src/
    types/index.ts              ← Domain types (User, CampusEvent, Registration…)
    data/demoData.ts            ← Demo users, events, registrations
    styles/global.css           ← All CSS (navy/blue university theme)
    components/
      Navbar.tsx                ← Sticky top navigation, role-aware links
      EventCard.tsx             ← Reusable event card with seat progress bar
      DemoBanner.tsx            ← Yellow demo-mode notice bar
    pages/
      LoginPage.tsx             ← Sign-in + Register form with demo shortcuts
      StudentDashboard.tsx      ← Stats, upcoming events, my registrations preview
      EventsPage.tsx            ← Searchable, filterable events grid
      EventDetailPage.tsx       ← Full event info + register widget
      MyRegistrations.tsx       ← Student's own registrations table
      MyCertificates.tsx        ← Certificate download cards
      AdminDashboard.tsx        ← Admin stats + events overview
      AdminEvents.tsx           ← Full CRUD modal for events
      AdminParticipants.tsx     ← Searchable participants list per event
      AdminCertificates.tsx     ← PDF upload modal + certificates table
    App.tsx                     ← Router (no react-router — simple state machine)
    main.tsx                    ← ReactDOM entry point

EventManagementSystem/backend/  ← Spring Boot backend (Milestone 2+)
  src/main/java/com/campus/events/
    model/                      Student, Event, Registration, Certificate
    dao/                        StudentDAO, EventDAO, RegistrationDAO, CertificateDAO
    service/                    AuthService, EventService, RegistrationService, CertificateService
    controller/                 AuthController, EventController, RegistrationController, CertificateController
    security/                   JwtUtil, JwtAuthFilter
    config/                     SecurityConfig, DatabaseConfig
    util/
      EventSorter.java          ← DSA: Merge Sort O(n log n) by event date
      WaitlistQueue.java        ← Java Collections: Queue<Long> for waitlist
  src/main/resources/
    schema.sql                  ← MySQL schema + seed data
    application.properties      ← Config (reads from env vars)
```

---

## 🚀 Milestone Roadmap

| # | Milestone | Status |
|---|-----------|--------|
| 1 | React frontend with all pages and demo data | ✅ **DONE** |
| 2 | MySQL schema + JDBC DAO layer | ⏳ Needs MySQL |
| 3 | Spring Boot auth (BCrypt + JWT) | ⏳ |
| 4 | Event CRUD, registration, participant list | ⏳ |
| 5 | Certificate upload/download (PDF) | ⏳ |
| 6 | End-to-end test + final README | ⏳ |

---

## 💾 MySQL Setup (Milestone 2)

### Install MySQL

**Option A — winget (recommended):**
```powershell
winget install Oracle.MySQL
```

**Option B — manual:**  
Download MySQL 8.0 Community Server from https://dev.mysql.com/downloads/mysql/

### Initialize database

```powershell
mysql -u root -p < EventManagementSystem\backend\src\main\resources\schema.sql
```

### Environment variables (set before starting backend)

```powershell
$env:DB_URL      = "jdbc:mysql://localhost:3306/campus_events?useSSL=false&serverTimezone=UTC"
$env:DB_USER     = "root"
$env:DB_PASSWORD = "your_mysql_password"
$env:JWT_SECRET  = "Campus3v3ntS3cr3tK3y2024SuperSecureKey!"
```

---

## ☕ Backend Startup (Milestone 3+)

```powershell
# Add Maven to PATH (already downloaded)
$env:PATH = "C:\tools\apache-maven-3.9.6\bin;$env:PATH"

# Build and run
cd EventManagementSystem\backend
mvn spring-boot:run
# Backend runs at http://localhost:8080
```

### Key API endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/auth/login | Public | Login → JWT |
| POST | /api/auth/register | Public | New student |
| GET | /api/events | Student/Admin | All published events (Merge Sorted) |
| POST | /api/events | Admin | Create event |
| PUT | /api/events/{id}/publish | Admin | Publish event |
| POST | /api/registrations/event/{id} | Student | Register (transaction-safe) |
| GET | /api/registrations/my | Student | Own registrations |
| POST | /api/certificates/upload | Admin | Upload PDF |
| GET | /api/certificates/download | Student/Admin | Download PDF |

---

## 🧠 DSA & Java Collections — Viva Reference

### Merge Sort (`EventSorter.java`)
- **Used in:** `EventService.getPublishedEvents()` and `getAllEvents()`
- **Complexity:** O(n log n) time · O(n) space
- **Why:** Stable sort (events on same date keep insertion order); consistent O(n log n) unlike QuickSort

### Java Collections Used

| Collection | Class | Where | Why |
|------------|-------|-------|-----|
| `ArrayList` | `java.util.ArrayList` | All DAOs, EventService | Ordered list of DB results |
| `HashMap` | `java.util.HashMap` | EventDAO (cache), JwtUtil (claims), AuthService (response) | O(1) key lookup |
| `HashSet` | `java.util.HashSet` | RegistrationDAO | O(1) duplicate registration pre-check |
| `Queue` | `java.util.Queue` (LinkedList) | WaitlistQueue | FIFO waitlist processing |

---

## 🔐 Security Design

- Passwords hashed with **BCrypt** (cost factor 12) — never stored in plaintext
- **JWT tokens** signed with HS256 — stateless, no server-side sessions
- Students can only access **their own** registrations and certificates (enforced by JWT `userId` claim)
- Certificate uploads: PDF-only validation, UUID stored filename, path traversal guard
- DB **UNIQUE constraint** on `(student_id, event_id)` + **transaction** prevents duplicate registration and overbooking
- All credentials in **environment variables**, never in source code
