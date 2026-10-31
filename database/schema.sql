-- ============================================================
-- Campus Event Management System — MySQL Database Schema
-- Run once to initialise. Re-running is safe (IF NOT EXISTS).
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus_events
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campus_events;

-- ============================================================
-- Table: students
-- Stores both STUDENT and ADMIN accounts (role column).
-- Unique constraints on email and register_number.
-- password_hash stores BCrypt output — never plaintext.
-- ============================================================
CREATE TABLE IF NOT EXISTS students (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    register_number VARCHAR(20)   NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    email           VARCHAR(150)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    department      VARCHAR(100)  DEFAULT '',
    year            VARCHAR(20)   DEFAULT '',
    role            ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT',
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_email           (email),
    UNIQUE KEY uq_register_number (register_number),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Table: events
-- status ENUM enforces valid states at DB level.
-- registered_count is maintained inside a transaction in RegistrationDAO
-- to prevent overbooking under concurrent requests.
-- ============================================================
CREATE TABLE IF NOT EXISTS events (
    id                    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    description           TEXT,
    instructions          TEXT,
    venue                 VARCHAR(200) DEFAULT '',
    event_date            DATETIME,
    registration_deadline DATETIME,
    total_seats           INT          NOT NULL DEFAULT 100,
    registered_count      INT          NOT NULL DEFAULT 0,
    status                ENUM('DRAFT','PUBLISHED','COMPLETED','CANCELLED')
                          NOT NULL DEFAULT 'DRAFT',
    created_by            BIGINT       NOT NULL,
    fee                   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    upi_qr_image          VARCHAR(255) DEFAULT NULL,
    is_deleted            TINYINT(1)   NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY fk_event_creator (created_by) REFERENCES students(id),
    INDEX idx_event_status (status),
    INDEX idx_event_date   (event_date),
    INDEX idx_event_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Table: registrations
-- UNIQUE KEY on (student_id, event_id) is the authoritative
-- guard against duplicate registrations — even under concurrency.
-- RegistrationDAO uses a transaction with SELECT FOR UPDATE
-- to also prevent overbooking.
-- ============================================================
CREATE TABLE IF NOT EXISTS registrations (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id     BIGINT NOT NULL,
    event_id       BIGINT NOT NULL,
    status         ENUM('REGISTERED','WAITLISTED','CANCELLED','PENDING')
                   NOT NULL DEFAULT 'REGISTERED',
    payment_status ENUM('NOT_REQUIRED','PENDING','APPROVED','REJECTED')
                   NOT NULL DEFAULT 'NOT_REQUIRED',
    registered_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_student_event (student_id, event_id),
    FOREIGN KEY fk_reg_student (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY fk_reg_event   (event_id)   REFERENCES events(id)   ON DELETE CASCADE,
    INDEX idx_reg_student (student_id),
    INDEX idx_reg_event   (event_id),
    INDEX idx_reg_payment_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Table: payment_submissions
-- Stores manual UPI payment submissions from students for paid events.
-- Verified by administrators before registration is confirmed.
-- ============================================================
CREATE TABLE IF NOT EXISTS payment_submissions (
    id               BIGINT         AUTO_INCREMENT PRIMARY KEY,
    registration_id  BIGINT         NOT NULL,
    student_id       BIGINT         NOT NULL,
    event_id         BIGINT         NOT NULL,
    amount           DECIMAL(10, 2) NOT NULL,
    transaction_id   VARCHAR(100)   NOT NULL,
    screenshot_path  VARCHAR(255)   NOT NULL,
    status           ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT           NULL,
    verified_by      BIGINT         NULL,
    submitted_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    verified_at      TIMESTAMP      NULL,

    UNIQUE KEY uq_transaction_id (transaction_id),
    FOREIGN KEY fk_pay_reg     (registration_id) REFERENCES registrations(id) ON DELETE CASCADE,
    FOREIGN KEY fk_pay_student (student_id)      REFERENCES students(id)      ON DELETE CASCADE,
    FOREIGN KEY fk_pay_event   (event_id)        REFERENCES events(id)        ON DELETE CASCADE,
    FOREIGN KEY fk_pay_admin   (verified_by)     REFERENCES students(id)      ON DELETE SET NULL,
    INDEX idx_pay_student_event (student_id, event_id),
    INDEX idx_pay_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Table: certificates
-- UNIQUE KEY on (student_id, event_id) — one cert per student per event.
-- Re-uploading is handled by ON DUPLICATE KEY UPDATE in CertificateDAO.
-- stored_file_name uses UUID prefix to prevent enumeration.
-- ============================================================
CREATE TABLE IF NOT EXISTS certificates (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    student_id        BIGINT       NOT NULL,
    event_id          BIGINT       NOT NULL,
    file_name         VARCHAR(255) NOT NULL,         -- original filename (for display)
    stored_file_name  VARCHAR(255) NOT NULL,         -- UUID-prefixed server filename
    file_size         BIGINT       DEFAULT 0,
    uploaded_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    uploaded_by       BIGINT       NOT NULL,         -- admin who uploaded (audit)

    UNIQUE KEY uq_cert_student_event (student_id, event_id),
    UNIQUE KEY uq_stored_file        (stored_file_name),
    FOREIGN KEY fk_cert_student    (student_id)  REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY fk_cert_event      (event_id)    REFERENCES events(id)   ON DELETE CASCADE,
    FOREIGN KEY fk_cert_uploader   (uploaded_by) REFERENCES students(id),
    INDEX idx_cert_student (student_id),
    INDEX idx_cert_event   (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Table: notifications
-- In-app notifications for students (e.g., registration deadline reminders).
-- UNIQUE KEY on (student_id, event_id, notification_type) ensures idempotency.
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    student_id        BIGINT       NOT NULL,
    event_id          BIGINT       NOT NULL,
    notification_type VARCHAR(50)  NOT NULL,
    message           TEXT         NOT NULL,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_student_event_type (student_id, event_id, notification_type),
    FOREIGN KEY fk_notif_student (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY fk_notif_event   (event_id)   REFERENCES events(id)   ON DELETE CASCADE,
    INDEX idx_notif_student_read (student_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Seed Data — Demo Accounts
--
-- Admin:   email = admin@campus.edu    password = Admin@1234
-- Student: email = student@campus.edu  password = Student@1234
--
-- BCrypt hashes generated with cost factor 12 using Spring's BCryptPasswordEncoder.
-- These are real, valid BCrypt hashes — the backend will verify them correctly.
-- ============================================================
INSERT IGNORE INTO students
    (register_number, name, email, password_hash, department, year, role)
VALUES
    -- Admin account (password = Admin@1234)
    ('ADMIN001', 'Campus Admin', 'admin@campus.edu',
     '$2a$12$RdGsz.FUuFYrqip3TXpX4.4P7K9hLxobREfSfnNAQUGJM4K2a/a8W',
     'Administration', 'N/A', 'ADMIN'),

    -- Demo student account (password = Student@1234)
    ('CS2024001', 'Arjun Sharma', 'student@campus.edu',
     '$2a$12$5gMXNy0gY8v9N9J2nXzHGO1L1qnKqfJCMD0MfcFLO.yiUJCiMJUHW',
     'Computer Science', '3rd Year', 'STUDENT');

-- ============================================================
-- Seed Events (created by admin — id=1 after the INSERT above)
-- ============================================================
INSERT IGNORE INTO events
    (title, description, instructions, venue,
     event_date, registration_deadline, total_seats, status, created_by)
VALUES
    ('Annual Tech Symposium 2024',
     'A full-day technology symposium featuring talks from industry leaders, live coding competitions, and networking sessions.',
     '1. Carry your college ID card at all times.
2. Report at the venue 30 minutes before the event.
3. Laptop optional but recommended for hands-on sessions.
4. Dress code: Smart casual.
5. Lunch and refreshments will be provided.',
     'Main Auditorium, Block A',
     DATE_ADD(NOW(), INTERVAL 15 DAY),
     DATE_ADD(NOW(), INTERVAL 10 DAY),
     200, 'PUBLISHED', 1),

    ('Web Development Workshop',
     'Hands-on 2-day workshop covering React, Spring Boot, and REST APIs. Build a real project from scratch.',
     '1. Bring your laptop with VS Code installed.
2. Install Node.js 20+ and Java 17 before attending.
3. A GitHub account is required for code submissions.
4. Pair programming is encouraged.',
     'Computer Lab 3, Block C',
     DATE_ADD(NOW(), INTERVAL 7 DAY),
     DATE_ADD(NOW(), INTERVAL 5 DAY),
     50, 'PUBLISHED', 1),

    ('Cultural Fest 2024 — Kaleidoscope',
     'Annual cultural festival with music, dance, drama, and art competitions. Open to all departments.',
     '1. Register for specific sub-events separately at the venue.
2. Entry is free for registered participants.
3. Guest passes available at the main gate.
4. Costumes and props must be arranged by participants.',
     'Open Air Theatre',
     DATE_ADD(NOW(), INTERVAL 30 DAY),
     DATE_ADD(NOW(), INTERVAL 25 DAY),
     500, 'PUBLISHED', 1),

    ('Interview Preparation Bootcamp',
     'Intensive 2-day bootcamp covering DSA, system design, and mock interviews with industry mentors.',
     '1. Pre-requisite: Basic knowledge of any programming language.
2. Submit your resume 3 days in advance.
3. Bring a notebook and pen.
4. Mock interviews conducted on Day 2.',
     'Seminar Hall 2, Block B',
     DATE_ADD(NOW(), INTERVAL 3 DAY),
     DATE_ADD(NOW(), INTERVAL 2 DAY),
     80, 'PUBLISHED', 1),

    ('Freshers Welcome Orientation 2024',
     'Official welcome ceremony for the 2024 batch. Meet faculty, seniors, and learn about campus resources.',
     '1. Mandatory for all 1st year students.
2. Bring original admission documents.
3. Parents are welcome to attend.',
     'Main Auditorium, Block A',
     DATE_ADD(NOW(), INTERVAL -5 DAY),
     DATE_ADD(NOW(), INTERVAL -7 DAY),
     400, 'COMPLETED', 1);

