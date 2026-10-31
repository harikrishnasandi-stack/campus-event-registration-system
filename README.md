# Campus Event Management System

A full-stack, enterprise-grade university event management and registration portal designed for college symposiums, workshops, technical bootcamps, and cultural festivals.

Built with **React + TypeScript + Vite**, **Java Spring Boot 3**, **MySQL 8.0**, and **Spring Security (JWT)** using pure **JDBC & the DAO pattern** (no JPA/Hibernate overhead).

---

## 🏛️ System Architecture & Technology Stack

| Layer | Technologies & Libraries | Key Responsibilities |
| :--- | :--- | :--- |
| **Frontend** | React 19, TypeScript, Vite | Modern responsive UI, role-aware routing, interactive modals, real-time unread badges, toast alerts. |
| **Backend** | Java 17+, Spring Boot 3.2.5 | RESTful APIs, Spring Security with stateless JWT (`HS256`), role-based access control (Admin/Student). |
| **Database** | MySQL 8.0+, HikariCP Connection Pool | Relational schema with strict foreign keys, unique constraints, and transaction isolation (`SELECT FOR UPDATE`). |
| **Data Access** | Pure JDBC (`PreparedStatement`, DAO Pattern) | Complete manual query control, SQL injection prevention, zero ORM caching bugs. |
| **Algorithms** | MergeSort $O(n \log n)$, FIFO Queue | Algorithmic event sorting by date and fair waitlist management for fully-booked events. |

---

## 📋 Prerequisites for Windows

Before starting, ensure the target evaluation machine has the following installed:

1. **Java JDK 17 or higher**: [Download OpenJDK / Oracle JDK](https://adoptium.net/)  
   *Verify in terminal:* `java -version`
2. **Node.js 18 or higher** (includes npm): [Download Node.js](https://nodejs.org/)  
   *Verify in terminal:* `node -v` and `npm -v`
3. **MySQL Server 8.0 or higher**: [Download MySQL Community Server](https://dev.mysql.com/downloads/installer/)  
   *Verify MySQL is running on port 3306.*

*(Note: Apache Maven does **not** need to be pre-installed; the project includes the self-contained Maven Wrapper `mvnw.cmd`).*

---

## 🚀 Quick Setup & Startup Instructions

### Step 1: Clone the Repository
```powershell
git clone https://github.com/harikrishnasandi-stack/campus-event-registration-system.git
cd campus-event-registration-system
```

---

### Step 2: Initialize the MySQL Database
Open PowerShell and pipe the provided idempotent SQL schema into MySQL. Replace `root` and enter your MySQL root password when prompted:

```powershell
# Using the schema file from the database directory:
mysql -u root -p < database\schema.sql
```
*(Alternatively, execute the script via MySQL Workbench or from `backend\src\main\resources\schema.sql`).*

**What this does:**
- Creates the `campus_events` database with UTF-8 character encoding.
- Creates all required relational tables: `students`, `events`, `registrations`, `payment_submissions`, `certificates`, and `notifications`.
- Configures foreign key cascades and unique constraints to prevent double booking.
- Inserts pre-seeded demo accounts with BCrypt-hashed passwords (cost factor 12).
- Inserts realistic upcoming, paid, free, and completed campus events.

---

### Step 3: Start the Spring Boot Backend

1. Navigate to the `backend` folder:
   ```powershell
   cd backend
   ```
2. If your local MySQL password differs from the default (`Campus2024!`), pass your password via environment variable:
   ```powershell
   $env:DB_PASSWORD="your_mysql_password"
   ```
3. Start the backend using the included Maven Wrapper:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. The backend will boot up on **`http://localhost:8080`**.  
   *Look for the log:* `Started EventManagementApplication in X.XXX seconds`.

---

### Step 4: Start the React Frontend

Open a **new PowerShell terminal**, navigate to the project root, and launch Vite:

```powershell
cd campus-event-registration-system
npm install
npm run dev
```

The frontend will start and display:
```
  ➜  Local:   http://localhost:5173/
```
Open **`http://localhost:5173/`** in your browser.

---

## 🔑 Pre-Configured Demo Accounts

You can use the one-click demo shortcuts directly on the login screen, or type the credentials manually:

| Role | Email | Password | Access Capabilities |
| :--- | :--- | :--- | :--- |
| **Campus Admin** | `admin@campus.edu` | `Admin@1234` | Create & edit events, upload UPI QR codes, cancel/delete events, verify & approve/reject student payments, view attendee rosters, upload participant PDF certificates. |
| **Student** | `student@campus.edu` | `Student@1234` | Browse upcoming events, free event registration, scan UPI QR for paid events & submit payment proof, download official registration receipts, view waitlist status, download certificates, notifications. |

*(New student accounts can also be created live using the "Register" tab on the login screen).*

---

## 🧪 Running Automated Tests

### Backend Unit & Integration Tests
Runs tests verifying event cancellation authorization, payment submissions & approval, receipt generation, safe event deletion, notification dispatch, idempotency, and deadline detection:
```powershell
cd backend
.\mvnw.cmd test
```
*Expected result:* `BUILD SUCCESS` (20 tests run, 0 failures, 0 errors across 3 test suites: `EventCancellationTest`, `EventDeletionAndPaymentTest`, `NotificationServiceTest`).

### Frontend Production Build Test
Verifies TypeScript type integrity and produces production bundle:
```powershell
cd ..
npm run build
```
*Expected result:* `✓ built in ...` with zero TypeScript or packaging errors.

---

## ⚙️ Environment Variables & Configuration

The application is fully configurable via environment variables or a `.env` file (template provided in `.env.example`):

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/campus_events...` | MySQL connection string. |
| `DB_USER` | `root` | MySQL username. |
| `DB_PASSWORD` | `Campus2024!` | MySQL password. |
| `JWT_SECRET` | `Campus3v3ntS3cr3tK3y2024SuperSecureKey!` | Secret key for signing HS256 JWT tokens. |
| `PORT` | `8080` | Spring Boot server port. |
| `UPLOAD_DIR` | `./uploads/certificates` | Directory for uploaded student certificates. |
| `CAMPUS_TIMEZONE`| `Asia/Kolkata` | Campus local timezone for event deadlines. |

---

## 📂 Repository Directory Structure

```
campus-event-registration-system/
├── .env.example               # Safe environment variable configuration template
├── .gitignore                 # Excludes node_modules, build targets, secrets, logs
├── package.json               # Frontend dependencies & scripts
├── vite.config.ts             # Vite configuration with /api backend proxy
├── tsconfig.json              # TypeScript compilation configuration
├── index.html                 # HTML entry point
│
├── database/
│   └── schema.sql             # Complete MySQL DDL schema + seed accounts & events
│
├── src/                       # React 19 Frontend
│   ├── api/client.ts          # Strongly-typed fetch API client & JWT token management
│   ├── components/            # UI components (Navbar, NotificationBell, EventCard, etc.)
│   ├── pages/                 # Portal views (Login, Dashboards, Events, Registrations, AdminPayments, Certs)
│   ├── styles/global.css      # Custom styling & responsive layouts
│   └── types/                 # Shared TypeScript interfaces
│
└── backend/                   # Spring Boot 3 Java Backend
    ├── pom.xml                # Maven configuration (Spring Boot, MySQL Driver, JJWT)
    ├── mvnw / mvnw.cmd        # Self-contained Maven Wrapper scripts
    ├── .mvn/wrapper/          # Maven wrapper properties & binaries
    └── src/
        ├── main/java/com/campus/events/
        │   ├── config/        # SecurityConfig, DatabaseConfig, WebMvcConfig
        │   ├── controller/    # REST Controllers (Auth, Events, Registrations, Payments, Notifications, etc.)
        │   ├── dao/           # Pure JDBC DAOs with PreparedStatement (User, Event, Reg, Payment, Notification, Cert)
        │   ├── model/         # Domain models (Student, Event, Registration, PaymentSubmission, Notification, Cert)
        │   ├── security/      # JwtUtil, JwtAuthFilter, PasswordEncoder
        │   ├── service/       # Business logic (Auth, Event, Payment, Waitlist, Registration, Certs)
        │   └── util/          # MergeSort implementation, WaitlistQueue
        ├── main/resources/
        │   ├── application.properties # Spring configuration with fallback defaults
        │   └── schema.sql     # Database schema mirror for backend resources
        └── test/java/com/campus/events/
            ├── EventCancellationTest.java      # Admin vs Student authorization tests
            ├── EventDeletionAndPaymentTest.java # Payment workflow & event deletion tests
            └── NotificationServiceTest.java     # Deadline calculation and idempotency tests
```
