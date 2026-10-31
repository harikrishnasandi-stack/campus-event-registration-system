package com.campus.events.dao;

import com.campus.events.model.PaymentSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Data Access Object for payment_submissions table.
 * Uses raw JDBC with PreparedStatement and strict transactions.
 */
@Repository
public class PaymentDAO {

    private static final Logger log = LoggerFactory.getLogger(PaymentDAO.class);

    @Autowired
    private DataSource dataSource;

    /** Save a new payment submission. Returns generated ID. */
    public Long save(PaymentSubmission p) {
        String sql = """
                INSERT INTO payment_submissions (registration_id, student_id, event_id, amount,
                                                transaction_id, screenshot_path, status, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, p.getRegistrationId());
            ps.setLong(2, p.getStudentId());
            ps.setLong(3, p.getEventId());
            ps.setBigDecimal(4, p.getAmount());
            ps.setString(5, p.getTransactionId().trim());
            ps.setString(6, p.getScreenshotPath());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error saving payment submission: {}", e.getMessage());
            throw new RuntimeException("Failed to save payment submission: " + e.getMessage());
        }
        return null;
    }

    /** Find submission by ID with joined student and event details. */
    public Optional<PaymentSubmission> findById(Long id) {
        String sql = """
                SELECT p.*, s.name AS student_name, s.register_number, s.email, s.department, s.year,
                       e.title AS event_title, e.event_date, e.venue,
                       admin_user.name AS verified_by_name
                FROM payment_submissions p
                JOIN students s ON p.student_id = s.id
                JOIN events e ON p.event_id = e.id
                LEFT JOIN students admin_user ON p.verified_by = admin_user.id
                WHERE p.id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding payment submission by ID {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    /** Find submission by transaction ID (to ensure uniqueness and prevent duplicates). */
    public Optional<PaymentSubmission> findByTransactionId(String txnId) {
        if (txnId == null || txnId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM payment_submissions WHERE transaction_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, txnId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowBasic(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error checking transaction ID {}: {}", txnId, e.getMessage());
        }
        return Optional.empty();
    }

    /** Find latest submission for a student and event. */
    public Optional<PaymentSubmission> findByStudentAndEvent(Long studentId, Long eventId) {
        String sql = """
                SELECT p.*, s.name AS student_name, s.register_number, s.email, s.department, s.year,
                       e.title AS event_title, e.event_date, e.venue,
                       admin_user.name AS verified_by_name
                FROM payment_submissions p
                JOIN students s ON p.student_id = s.id
                JOIN events e ON p.event_id = e.id
                LEFT JOIN students admin_user ON p.verified_by = admin_user.id
                WHERE p.student_id = ? AND p.event_id = ?
                ORDER BY p.submitted_at DESC
                LIMIT 1
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            ps.setLong(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding payment for student {} event {}: {}", studentId, eventId, e.getMessage());
        }
        return Optional.empty();
    }

    /** Find submission by registration ID. */
    public Optional<PaymentSubmission> findByRegistrationId(Long registrationId) {
        String sql = """
                SELECT p.*, s.name AS student_name, s.register_number, s.email, s.department, s.year,
                       e.title AS event_title, e.event_date, e.venue,
                       admin_user.name AS verified_by_name
                FROM payment_submissions p
                JOIN students s ON p.student_id = s.id
                JOIN events e ON p.event_id = e.id
                LEFT JOIN students admin_user ON p.verified_by = admin_user.id
                WHERE p.registration_id = ?
                ORDER BY p.submitted_at DESC
                LIMIT 1
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, registrationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding payment by registrationId {}: {}", registrationId, e.getMessage());
        }
        return Optional.empty();
    }

    /** Retrieve all payment submissions, optionally filtered by status (ALL, PENDING, APPROVED, REJECTED). */
    public List<PaymentSubmission> findAll(String statusFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.*, s.name AS student_name, s.register_number, s.email, s.department, s.year,
                       e.title AS event_title, e.event_date, e.venue,
                       admin_user.name AS verified_by_name
                FROM payment_submissions p
                JOIN students s ON p.student_id = s.id
                JOIN events e ON p.event_id = e.id
                LEFT JOIN students admin_user ON p.verified_by = admin_user.id
                """);

        boolean hasFilter = statusFilter != null && !statusFilter.equalsIgnoreCase("ALL") && !statusFilter.isBlank();
        if (hasFilter) {
            sql.append(" WHERE p.status = ?");
        }
        sql.append(" ORDER BY p.submitted_at DESC");

        List<PaymentSubmission> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            if (hasFilter) {
                ps.setString(1, statusFilter.toUpperCase());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error fetching payment submissions: {}", e.getMessage());
        }
        return list;
    }

    /** Get counts for dashboard filters: ALL, PENDING, APPROVED, REJECTED. */
    public Map<String, Integer> countByStatus() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("ALL", 0);
        counts.put("PENDING", 0);
        counts.put("APPROVED", 0);
        counts.put("REJECTED", 0);

        String sql = "SELECT status, COUNT(*) AS cnt FROM payment_submissions GROUP BY status";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int total = 0;
            while (rs.next()) {
                String st = rs.getString("status");
                int c = rs.getInt("cnt");
                counts.put(st.toUpperCase(), c);
                total += c;
            }
            counts.put("ALL", total);
        } catch (SQLException e) {
            log.error("Error counting payments by status: {}", e.getMessage());
        }
        return counts;
    }

    /**
     * Atomically approve payment:
     * 1. Locks payment submission row FOR UPDATE.
     * 2. Checks idempotency (if already APPROVED, returns).
     * 3. Updates payment status to APPROVED, records admin ID and timestamp.
     * 4. Updates registration status to REGISTERED and payment_status to APPROVED.
     * 5. Atomically increments event registered_count.
     * 6. Commits transaction.
     */
    public PaymentSubmission approvePayment(Long paymentId, Long adminId) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false); // BEGIN TRANSACTION

            // 1. Lock payment row
            String lockSql = "SELECT * FROM payment_submissions WHERE id = ? FOR UPDATE";
            PreparedStatement lockPs = conn.prepareStatement(lockSql);
            lockPs.setLong(1, paymentId);
            ResultSet rs = lockPs.executeQuery();

            if (!rs.next()) {
                conn.rollback();
                throw new IllegalArgumentException("Payment submission not found with ID: " + paymentId);
            }

            String currentStatus = rs.getString("status");
            Long regId = rs.getLong("registration_id");
            Long eventId = rs.getLong("event_id");

            if ("APPROVED".equalsIgnoreCase(currentStatus)) {
                conn.rollback();
                return findById(paymentId).orElseThrow();
            }

            // 2. Update payment submission
            String updatePaySql = "UPDATE payment_submissions SET status = 'APPROVED', verified_by = ?, verified_at = NOW(), rejection_reason = NULL WHERE id = ?";
            try (PreparedStatement upPs = conn.prepareStatement(updatePaySql)) {
                upPs.setLong(1, adminId);
                upPs.setLong(2, paymentId);
                upPs.executeUpdate();
            }

            // 3. Update registration
            String updateRegSql = "UPDATE registrations SET status = 'REGISTERED', payment_status = 'APPROVED' WHERE id = ?";
            try (PreparedStatement urPs = conn.prepareStatement(updateRegSql)) {
                urPs.setLong(1, regId);
                urPs.executeUpdate();
            }

            // 4. Increment event seat count
            String incrSql = "UPDATE events SET registered_count = registered_count + 1 WHERE id = ?";
            try (PreparedStatement inPs = conn.prepareStatement(incrSql)) {
                inPs.setLong(1, eventId);
                inPs.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION
            return findById(paymentId).orElseThrow();

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
            }
            log.error("Error approving payment {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to approve payment: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { log.error("Close failed", ex); }
            }
        }
    }

    /**
     * Atomically reject payment:
     * 1. Locks payment submission row FOR UPDATE.
     * 2. Updates payment status to REJECTED, stores rejection reason, records admin ID and timestamp.
     * 3. Updates registration payment_status to REJECTED.
     * 4. Commits transaction.
     */
    public PaymentSubmission rejectPayment(Long paymentId, String reason, Long adminId) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false); // BEGIN TRANSACTION

            String lockSql = "SELECT * FROM payment_submissions WHERE id = ? FOR UPDATE";
            PreparedStatement lockPs = conn.prepareStatement(lockSql);
            lockPs.setLong(1, paymentId);
            ResultSet rs = lockPs.executeQuery();

            if (!rs.next()) {
                conn.rollback();
                throw new IllegalArgumentException("Payment submission not found with ID: " + paymentId);
            }

            Long regId = rs.getLong("registration_id");

            // Update payment submission
            String updatePaySql = "UPDATE payment_submissions SET status = 'REJECTED', rejection_reason = ?, verified_by = ?, verified_at = NOW() WHERE id = ?";
            try (PreparedStatement upPs = conn.prepareStatement(updatePaySql)) {
                upPs.setString(1, reason);
                upPs.setLong(2, adminId);
                upPs.setLong(3, paymentId);
                upPs.executeUpdate();
            }

            // Update registration
            String updateRegSql = "UPDATE registrations SET payment_status = 'REJECTED' WHERE id = ?";
            try (PreparedStatement urPs = conn.prepareStatement(updateRegSql)) {
                urPs.setLong(1, regId);
                urPs.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION
            return findById(paymentId).orElseThrow();

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
            }
            log.error("Error rejecting payment {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to reject payment: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { log.error("Close failed", ex); }
            }
        }
    }

    private PaymentSubmission mapRow(ResultSet rs) throws SQLException {
        PaymentSubmission p = mapRowBasic(rs);
        try { p.setStudentName(rs.getString("student_name")); } catch (SQLException ignored) {}
        try { p.setRegisterNumber(rs.getString("register_number")); } catch (SQLException ignored) {}
        try { p.setEmail(rs.getString("email")); } catch (SQLException ignored) {}
        try { p.setDepartment(rs.getString("department")); } catch (SQLException ignored) {}
        try { p.setYear(rs.getString("year")); } catch (SQLException ignored) {}
        try { p.setEventTitle(rs.getString("event_title")); } catch (SQLException ignored) {}
        try {
            Timestamp ed = rs.getTimestamp("event_date");
            if (ed != null) p.setEventDate(ed.toLocalDateTime());
        } catch (SQLException ignored) {}
        try { p.setVenue(rs.getString("venue")); } catch (SQLException ignored) {}
        try { p.setVerifiedByName(rs.getString("verified_by_name")); } catch (SQLException ignored) {}
        return p;
    }

    private PaymentSubmission mapRowBasic(ResultSet rs) throws SQLException {
        PaymentSubmission p = new PaymentSubmission();
        p.setId(rs.getLong("id"));
        p.setRegistrationId(rs.getLong("registration_id"));
        p.setStudentId(rs.getLong("student_id"));
        p.setEventId(rs.getLong("event_id"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setTransactionId(rs.getString("transaction_id"));
        p.setScreenshotPath(rs.getString("screenshot_path"));
        p.setStatus(rs.getString("status"));
        p.setRejectionReason(rs.getString("rejection_reason"));

        long vb = rs.getLong("verified_by");
        if (!rs.wasNull()) p.setVerifiedBy(vb);

        Timestamp sa = rs.getTimestamp("submitted_at");
        if (sa != null) p.setSubmittedAt(sa.toLocalDateTime());

        Timestamp va = rs.getTimestamp("verified_at");
        if (va != null) p.setVerifiedAt(va.toLocalDateTime());

        return p;
    }
}

