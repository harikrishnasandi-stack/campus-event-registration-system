package com.campus.events.dao;

import com.campus.events.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Events table.
 * Uses raw JDBC with PreparedStatement — no JPA/Hibernate.
 *
 * Java Collections used:
 *   - ArrayList to return lists of events
 *   - HashMap as an in-memory event cache (eventId -> Event)
 */
@Repository
public class EventDAO {

    private static final Logger log = LoggerFactory.getLogger(EventDAO.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Java Collections: HashMap used as an event lookup cache.
     * Key = eventId (Long), Value = Event object
     * Reduces repeated DB queries for frequently accessed events.
     * Time complexity for lookup: O(1) average case.
     */
    private final HashMap<Long, Event> eventCache = new HashMap<>();

    /** Retrieve all published events (for students). Returns ArrayList. Filters out deleted events. */
    public ArrayList<Event> findAllPublished() {
        String sql = "SELECT * FROM events WHERE status = 'PUBLISHED' AND is_deleted = FALSE ORDER BY event_date ASC";
        ArrayList<Event> events = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Event e = mapRow(rs);
                events.add(e);
                eventCache.put(e.getId(), e); // populate cache
            }
        } catch (SQLException e) {
            log.error("Error fetching published events: {}", e.getMessage());
        }
        return events;
    }

    /** Retrieve all active events (for admin). Excludes deleted events. Returns ArrayList. */
    public ArrayList<Event> findAll() {
        String sql = "SELECT * FROM events WHERE is_deleted = FALSE ORDER BY event_date DESC";
        ArrayList<Event> events = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Event e = mapRow(rs);
                events.add(e);
                eventCache.put(e.getId(), e);
            }
        } catch (SQLException e) {
            log.error("Error fetching all events: {}", e.getMessage());
        }
        return events;
    }

    /** Find event by ID — uses cache first (HashMap O(1) lookup). */
    public Optional<Event> findById(Long id) {
        // Check HashMap cache first
        if (eventCache.containsKey(id)) {
            log.debug("Cache hit for event {}", id);
            return Optional.of(eventCache.get(id));
        }

        // Cache miss — query database
        String sql = "SELECT * FROM events WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Event event = mapRow(rs);
                eventCache.put(id, event); // store in cache
                return Optional.of(event);
            }
        } catch (SQLException e) {
            log.error("Error finding event by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Insert a new event. Returns generated ID. */
    public Long save(Event event) {
        String sql = """
                INSERT INTO events (title, description, instructions, venue, event_date,
                                    registration_deadline, total_seats, registered_count, status, created_by,
                                    fee, upi_qr_image, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 0)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, event.getTitle());
            ps.setString(2, event.getDescription());
            ps.setString(3, event.getInstructions());
            ps.setString(4, event.getVenue());
            ps.setTimestamp(5, event.getEventDate() != null ? Timestamp.valueOf(event.getEventDate()) : null);
            ps.setTimestamp(6, event.getRegistrationDeadline() != null ? Timestamp.valueOf(event.getRegistrationDeadline()) : null);
            ps.setInt(7, event.getTotalSeats());
            ps.setString(8, event.getStatus() != null ? event.getStatus() : "DRAFT");
            ps.setLong(9, event.getCreatedBy());
            ps.setBigDecimal(10, event.getFee() != null ? event.getFee() : BigDecimal.ZERO);
            ps.setString(11, event.getUpiQrImage());

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                Long id = keys.getLong(1);
                eventCache.remove(id); // invalidate cache
                return id;
            }

        } catch (SQLException e) {
            log.error("Error saving event: {}", e.getMessage());
            throw new RuntimeException("Failed to save event: " + e.getMessage());
        }
        return null;
    }

    /** Update an existing event. */
    public boolean update(Event event) {
        String sql = """
                UPDATE events SET title=?, description=?, instructions=?, venue=?,
                event_date=?, registration_deadline=?, total_seats=?, status=?,
                fee=?, upi_qr_image=?, updated_at=NOW()
                WHERE id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, event.getTitle());
            ps.setString(2, event.getDescription());
            ps.setString(3, event.getInstructions());
            ps.setString(4, event.getVenue());
            ps.setTimestamp(5, event.getEventDate() != null ? Timestamp.valueOf(event.getEventDate()) : null);
            ps.setTimestamp(6, event.getRegistrationDeadline() != null ? Timestamp.valueOf(event.getRegistrationDeadline()) : null);
            ps.setInt(7, event.getTotalSeats());
            ps.setString(8, event.getStatus());
            ps.setBigDecimal(9, event.getFee() != null ? event.getFee() : BigDecimal.ZERO);
            ps.setString(10, event.getUpiQrImage());
            ps.setLong(11, event.getId());

            int rows = ps.executeUpdate();
            eventCache.remove(event.getId()); // invalidate cache
            return rows > 0;

        } catch (SQLException e) {
            log.error("Error updating event: {}", e.getMessage());
            return false;
        }
    }

    /** Update only the status of an event. */
    public boolean updateStatus(Long eventId, String status) {
        String sql = "UPDATE events SET status=?, updated_at=NOW() WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setLong(2, eventId);

            int rows = ps.executeUpdate();
            eventCache.remove(eventId); // invalidate cache
            return rows > 0;

        } catch (SQLException e) {
            log.error("Error updating event status: {}", e.getMessage());
            return false;
        }
    }

    /** Update UPI QR image path for an event. */
    public boolean updateQrImage(Long eventId, String qrImagePath) {
        String sql = "UPDATE events SET upi_qr_image=?, updated_at=NOW() WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, qrImagePath);
            ps.setLong(2, eventId);

            int rows = ps.executeUpdate();
            eventCache.remove(eventId);
            return rows > 0;
        } catch (SQLException e) {
            log.error("Error updating QR image for event {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    /** Check if event has dependent registrations, payments, or certificates. */
    public boolean hasDependentRecords(Long eventId) {
        String checkRegs = "SELECT COUNT(*) FROM registrations WHERE event_id = ?";
        String checkPayments = "SELECT COUNT(*) FROM payment_submissions WHERE event_id = ?";
        String checkCerts = "SELECT COUNT(*) FROM certificates WHERE event_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(checkRegs)) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) return true;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(checkPayments)) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) return true;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(checkCerts)) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) return true;
                }
            }
        } catch (SQLException e) {
            log.error("Error checking dependent records for event {}: {}", eventId, e.getMessage());
        }
        return false;
    }

    /**
     * Soft delete / archive an event that has historical records.
     * Hides it from listings while preserving registration, payment audit, and certificates.
     */
    public boolean softDelete(Long eventId) {
        String sql = "UPDATE events SET is_deleted = TRUE, status = 'CANCELLED', updated_at = NOW() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, eventId);
            int rows = ps.executeUpdate();
            eventCache.remove(eventId);
            return rows > 0;
        } catch (SQLException e) {
            log.error("Error soft deleting event {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    /**
     * Hard delete an event that has no dependent records.
     * Cleans up any notifications first, then permanently deletes the event row.
     */
    public boolean hardDelete(Long eventId) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            // Clean up notifications for this event if any
            String notifSql = "DELETE FROM notifications WHERE event_id = ?";
            try (PreparedStatement notifPs = conn.prepareStatement(notifSql)) {
                notifPs.setLong(1, eventId);
                notifPs.executeUpdate();
            }

            // Permanently delete event
            String eventSql = "DELETE FROM events WHERE id = ?";
            int rows;
            try (PreparedStatement eventPs = conn.prepareStatement(eventSql)) {
                eventPs.setLong(1, eventId);
                rows = eventPs.executeUpdate();
            }

            conn.commit();
            eventCache.remove(eventId);
            return rows > 0;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
            }
            log.error("Error hard deleting event {}: {}", eventId, e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { log.error("Close failed", ex); }
            }
        }
    }

    /** Map a ResultSet row to an Event object. */
    private Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getLong("id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setInstructions(rs.getString("instructions"));
        e.setVenue(rs.getString("venue"));

        Timestamp eventDate = rs.getTimestamp("event_date");
        if (eventDate != null) e.setEventDate(eventDate.toLocalDateTime());

        Timestamp deadline = rs.getTimestamp("registration_deadline");
        if (deadline != null) e.setRegistrationDeadline(deadline.toLocalDateTime());

        e.setTotalSeats(rs.getInt("total_seats"));
        e.setRegisteredCount(rs.getInt("registered_count"));
        e.setStatus(rs.getString("status"));
        e.setCreatedBy(rs.getLong("created_by"));

        BigDecimal fee = rs.getBigDecimal("fee");
        e.setFee(fee != null ? fee : BigDecimal.ZERO);
        e.setUpiQrImage(rs.getString("upi_qr_image"));
        e.setDeleted(rs.getBoolean("is_deleted"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) e.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) e.setUpdatedAt(updatedAt.toLocalDateTime());

        return e;
    }
}
