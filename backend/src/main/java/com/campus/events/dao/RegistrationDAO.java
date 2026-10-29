package com.campus.events.dao;

import com.campus.events.model.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

/**
 * Data Access Object for Registrations table.
 * Uses raw JDBC with PreparedStatement — no JPA/Hibernate.
 *
 * Java Collections used:
 *   - ArrayList for lists of registrations
 *   - HashSet for fast duplicate registration checks (O(1) lookup)
 *
 * NOTE: HashSet is used as a fast in-memory pre-check.
 * The database UNIQUE constraint (student_id, event_id) is the authoritative guard.
 */
@Repository
public class RegistrationDAO {

    private static final Logger log = LoggerFactory.getLogger(RegistrationDAO.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Java Collections: HashSet for duplicate registration pre-check.
     * Stores "studentId:eventId" strings for O(1) contains() check.
     * Initialized lazily; kept consistent by invalidating on write.
     */
    private final HashSet<String> registeredPairs = new HashSet<>();
    private boolean cacheLoaded = false;

    private synchronized void ensureCacheLoaded() {
        if (cacheLoaded) return;
        String sql = "SELECT student_id, event_id FROM registrations WHERE status = 'REGISTERED'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                registeredPairs.add(rs.getLong("student_id") + ":" + rs.getLong("event_id"));
            }
            cacheLoaded = true;
        } catch (SQLException e) {
            log.error("Error loading registration cache: {}", e.getMessage());
        }
    }

    /**
     * Check if a student is already registered (HashSet O(1) lookup).
     * This is a fast pre-check; DB constraint is the authoritative guard.
     */
    public boolean isAlreadyRegistered(Long studentId, Long eventId) {
        ensureCacheLoaded();
        return registeredPairs.contains(studentId + ":" + eventId);
    }

    /**
     * Register a student for an event.
     * Uses a TRANSACTION to atomically:
     *   1. Verify available seats (SELECT ... FOR UPDATE)
     *   2. Insert registration record
     *   3. Increment registered_count on events table
     * This prevents overbooking under concurrent requests.
     *
     * @return "REGISTERED", "WAITLISTED", or throws exception on duplicate/error
     */
    public String registerStudent(Long studentId, Long eventId) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false); // BEGIN TRANSACTION

            // Lock the event row for update to prevent race conditions
            String lockSql = "SELECT total_seats, registered_count, status, registration_deadline FROM events WHERE id = ? FOR UPDATE";
            PreparedStatement lockPs = conn.prepareStatement(lockSql);
            lockPs.setLong(1, eventId);
            ResultSet lockRs = lockPs.executeQuery();

            if (!lockRs.next()) {
                conn.rollback();
                throw new RuntimeException("Event not found: " + eventId);
            }

            String eventStatus = lockRs.getString("status");
            Timestamp deadline = lockRs.getTimestamp("registration_deadline");
            int totalSeats = lockRs.getInt("total_seats");
            int registeredCount = lockRs.getInt("registered_count");

            // Validate event is open for registration
            if (!"PUBLISHED".equals(eventStatus)) {
                conn.rollback();
                throw new RuntimeException("Event is not open for registration");
            }
            if (deadline != null && deadline.toLocalDateTime().isBefore(java.time.LocalDateTime.now())) {
                conn.rollback();
                throw new RuntimeException("Registration deadline has passed");
            }

            // Determine registration status
            String regStatus = (registeredCount < totalSeats) ? "REGISTERED" : "WAITLISTED";

            // Insert registration record
            String insertSql = "INSERT INTO registrations (student_id, event_id, status) VALUES (?, ?, ?)";
            PreparedStatement insertPs = conn.prepareStatement(insertSql);
            insertPs.setLong(1, studentId);
            insertPs.setLong(2, eventId);
            insertPs.setString(3, regStatus);
            insertPs.executeUpdate();

            // If REGISTERED, increment seat count
            if ("REGISTERED".equals(regStatus)) {
                String updateSql = "UPDATE events SET registered_count = registered_count + 1 WHERE id = ?";
                PreparedStatement updatePs = conn.prepareStatement(updateSql);
                updatePs.setLong(1, eventId);
                updatePs.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            // Update HashSet cache
            if ("REGISTERED".equals(regStatus)) {
                registeredPairs.add(studentId + ":" + eventId);
            }

            return regStatus;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
            }
            log.error("Error registering student: {}", e.getMessage());
            throw new RuntimeException("Registration failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { log.error("Error closing connection", e); }
            }
        }
    }

    /**
     * Cancel a registration and promote the next waitlisted student (if any).
     * Uses a TRANSACTION to atomically handle the cancellation and promotion.
     */
    public boolean cancelRegistration(Long studentId, Long eventId) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            // Get current registration status
            String findSql = "SELECT id, status FROM registrations WHERE student_id = ? AND event_id = ?";
            PreparedStatement findPs = conn.prepareStatement(findSql);
            findPs.setLong(1, studentId);
            findPs.setLong(2, eventId);
            ResultSet findRs = findPs.executeQuery();

            if (!findRs.next()) {
                conn.rollback();
                return false;
            }

            String currentStatus = findRs.getString("status");

            // Cancel the registration
            String cancelSql = "UPDATE registrations SET status = 'CANCELLED' WHERE student_id = ? AND event_id = ?";
            PreparedStatement cancelPs = conn.prepareStatement(cancelSql);
            cancelPs.setLong(1, studentId);
            cancelPs.setLong(2, eventId);
            cancelPs.executeUpdate();

            // If was REGISTERED, decrement seat count and promote next waitlisted
            if ("REGISTERED".equals(currentStatus)) {
                String decrSql = "UPDATE events SET registered_count = registered_count - 1 WHERE id = ?";
                PreparedStatement decrPs = conn.prepareStatement(decrSql);
                decrPs.setLong(1, eventId);
                decrPs.executeUpdate();

                // Promote next waitlisted student (FIFO — earliest registered_at)
                String waitlistSql = "SELECT id, student_id FROM registrations WHERE event_id = ? AND status = 'WAITLISTED' ORDER BY registered_at ASC LIMIT 1";
                PreparedStatement waitPs = conn.prepareStatement(waitlistSql);
                waitPs.setLong(1, eventId);
                ResultSet waitRs = waitPs.executeQuery();

                if (waitRs.next()) {
                    long waitId = waitRs.getLong("id");
                    long waitStudentId = waitRs.getLong("student_id");

                    String promoteSql = "UPDATE registrations SET status = 'REGISTERED' WHERE id = ?";
                    PreparedStatement promotePs = conn.prepareStatement(promoteSql);
                    promotePs.setLong(1, waitId);
                    promotePs.executeUpdate();

                    String incrSql = "UPDATE events SET registered_count = registered_count + 1 WHERE id = ?";
                    PreparedStatement incrPs = conn.prepareStatement(incrSql);
                    incrPs.setLong(1, eventId);
                    incrPs.executeUpdate();

                    // Update HashSet cache for promoted student
                    registeredPairs.add(waitStudentId + ":" + eventId);
                }
            }

            conn.commit();

            // Invalidate cache for cancelled student
            registeredPairs.remove(studentId + ":" + eventId);
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
            }
            log.error("Error cancelling registration: {}", e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { log.error("Error closing connection", e); }
            }
        }
    }

    /** Find registrations by student ID (for student's my-events view). */
    public ArrayList<Registration> findByStudentId(Long studentId) {
        String sql = """
                SELECT r.*, e.title AS event_title, e.event_date, e.status AS event_status
                FROM registrations r
                JOIN events e ON r.event_id = e.id
                WHERE r.student_id = ?
                ORDER BY r.registered_at DESC
                """;
        ArrayList<Registration> registrations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                registrations.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error fetching registrations by student: {}", e.getMessage());
        }
        return registrations;
    }

    /** Find registrations by event ID (for admin participant list). */
    public ArrayList<Registration> findByEventId(Long eventId) {
        String sql = """
                SELECT r.*, s.name AS student_name, s.register_number, s.email, s.department, s.year,
                       e.title AS event_title, e.event_date, e.status AS event_status
                FROM registrations r
                JOIN students s ON r.student_id = s.id
                JOIN events e ON r.event_id = e.id
                WHERE r.event_id = ?
                ORDER BY r.status, r.registered_at ASC
                """;
        ArrayList<Registration> registrations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, eventId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                registrations.add(mapRowWithJoin(rs));
            }
        } catch (SQLException e) {
            log.error("Error fetching registrations by event: {}", e.getMessage());
        }
        return registrations;
    }

    /** Find a specific registration by student and event IDs. */
    public Optional<Registration> findByStudentAndEvent(Long studentId, Long eventId) {
        String sql = "SELECT * FROM registrations WHERE student_id = ? AND event_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            ps.setLong(2, eventId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error finding registration: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Map a ResultSet row to a Registration object (basic fields). */
    private Registration mapRow(ResultSet rs) throws SQLException {
        Registration r = new Registration();
        r.setId(rs.getLong("id"));
        r.setStudentId(rs.getLong("student_id"));
        r.setEventId(rs.getLong("event_id"));
        r.setStatus(rs.getString("status"));
        Timestamp registeredAt = rs.getTimestamp("registered_at");
        if (registeredAt != null) r.setRegisteredAt(registeredAt.toLocalDateTime());

        // Optional joined fields (may be null if not in query)
        try { r.setEventTitle(rs.getString("event_title")); } catch (SQLException ignored) {}
        try {
            Timestamp ed = rs.getTimestamp("event_date");
            if (ed != null) r.setEventDate(ed.toLocalDateTime());
        } catch (SQLException ignored) {}
        try { r.setEventStatus(rs.getString("event_status")); } catch (SQLException ignored) {}

        return r;
    }

    /** Map a ResultSet row with full join fields. */
    private Registration mapRowWithJoin(ResultSet rs) throws SQLException {
        Registration r = mapRow(rs);
        try { r.setStudentName(rs.getString("student_name")); } catch (SQLException ignored) {}
        try { r.setRegisterNumber(rs.getString("register_number")); } catch (SQLException ignored) {}
        try { r.setEmail(rs.getString("email")); } catch (SQLException ignored) {}
        try { r.setDepartment(rs.getString("department")); } catch (SQLException ignored) {}
        try { r.setYear(rs.getString("year")); } catch (SQLException ignored) {}
        return r;
    }
}
