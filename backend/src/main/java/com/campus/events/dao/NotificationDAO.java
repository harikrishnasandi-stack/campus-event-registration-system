package com.campus.events.dao;

import com.campus.events.model.Event;
import com.campus.events.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Notifications table.
 * Uses raw JDBC PreparedStatement (no JPA/Hibernate).
 *
 * Java Collections:
 *  - ArrayList<Notification> for query results
 *  - ArrayList<Long> for batch student IDs
 */
@Repository
public class NotificationDAO {

    private static final Logger log = LoggerFactory.getLogger(NotificationDAO.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Save a single notification.
     * Uses INSERT IGNORE so duplicate (student_id, event_id, notification_type) rows are silently ignored.
     * @return true if a new record was inserted, false if already existed.
     */
    public boolean save(Notification notification) {
        String sql = """
                INSERT IGNORE INTO notifications
                    (student_id, event_id, notification_type, message, is_read, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, notification.getStudentId());
            ps.setLong(2, notification.getEventId());
            ps.setString(3, notification.getNotificationType());
            ps.setString(4, notification.getMessage());
            ps.setBoolean(5, notification.isRead());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        notification.setId(rs.getLong(1));
                    }
                }
                return true;
            }
            return false;

        } catch (SQLException e) {
            log.error("Failed to save notification: {}", e.getMessage());
            throw new RuntimeException("Database error saving notification", e);
        }
    }

    /**
     * Batch save notifications using JDBC batching and INSERT IGNORE.
     * Guarantees idempotency via database unique constraint.
     */
    public int saveAll(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) return 0;

        String sql = """
                INSERT IGNORE INTO notifications
                    (student_id, event_id, notification_type, message, is_read, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            for (Notification n : notifications) {
                ps.setLong(1, n.getStudentId());
                ps.setLong(2, n.getEventId());
                ps.setString(3, n.getNotificationType());
                ps.setString(4, n.getMessage());
                ps.setBoolean(5, n.isRead());
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            conn.commit();

            int inserted = 0;
            for (int r : results) {
                if (r > 0 || r == Statement.SUCCESS_NO_INFO) inserted++;
            }
            return inserted;

        } catch (SQLException e) {
            log.error("Failed to batch save notifications: {}", e.getMessage());
            throw new RuntimeException("Database error in batch save notifications", e);
        }
    }

    /**
     * Get all notifications for a specific student, sorted newest first.
     * Enriches notification with event title and registration deadline from events table.
     */
    public ArrayList<Notification> findByStudentId(Long studentId) {
        String sql = """
                SELECT n.id, n.student_id, n.event_id, n.notification_type, n.message,
                       n.is_read, n.created_at, e.title AS event_title, e.registration_deadline
                FROM notifications n
                LEFT JOIN events e ON n.event_id = e.id
                WHERE n.student_id = ?
                ORDER BY n.created_at DESC, n.id DESC
                """;
        ArrayList<Notification> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;

        } catch (SQLException e) {
            log.error("Failed to find notifications for student {}: {}", studentId, e.getMessage());
            throw new RuntimeException("Database error fetching notifications", e);
        }
    }

    /**
     * Count unread notifications for a student.
     */
    public int countUnreadByStudentId(Long studentId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE student_id = ? AND is_read = FALSE";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            log.error("Failed to count unread notifications for student {}: {}", studentId, e.getMessage());
            throw new RuntimeException("Database error counting unread notifications", e);
        }
    }

    /**
     * Mark a specific notification as read.
     * Enforces ownership at DB query level (must match id AND student_id).
     * @return Optional.empty() if not found or unauthorized;
     *         Optional.of(true) if it was unread and now marked read;
     *         Optional.of(false) if it was already marked as read.
     */
    public Optional<Boolean> markAsRead(Long id, Long studentId) {
        String checkSql = "SELECT is_read FROM notifications WHERE id = ? AND student_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {

            ps.setLong(1, id);
            ps.setLong(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty(); // Not found or access denied
                }
                boolean currentRead = rs.getBoolean("is_read");
                if (!currentRead) {
                    String updateSql = "UPDATE notifications SET is_read = TRUE WHERE id = ? AND student_id = ?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setLong(1, id);
                        updatePs.setLong(2, studentId);
                        updatePs.executeUpdate();
                    }
                    return Optional.of(true); // Was unread, marked as read
                }
                return Optional.of(false); // Already read
            }

        } catch (SQLException e) {
            log.error("Failed to mark notification {} as read: {}", id, e.getMessage());
            throw new RuntimeException("Database error updating notification", e);
        }
    }

    /**
     * Mark all notifications for a student as read.
     */
    public int markAllAsRead(Long studentId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE student_id = ? AND is_read = FALSE";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            return ps.executeUpdate();

        } catch (SQLException e) {
            log.error("Failed to mark all notifications as read for student {}: {}", studentId, e.getMessage());
            throw new RuntimeException("Database error updating all notifications", e);
        }
    }

    /**
     * Query published events whose registration deadline is between start and end.
     * Excludes CANCELLED, DRAFT, and COMPLETED events.
     */
    public ArrayList<Event> findEligibleDeadlineEvents(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT id, title, description, instructions, venue, event_date,
                       registration_deadline, total_seats, registered_count, status, created_by
                FROM events
                WHERE status = 'PUBLISHED'
                  AND registration_deadline >= ?
                  AND registration_deadline < ?
                ORDER BY registration_deadline ASC
                """;
        ArrayList<Event> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
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

                    list.add(e);
                }
            }
            return list;

        } catch (SQLException e) {
            log.error("Failed to query eligible deadline events: {}", e.getMessage());
            throw new RuntimeException("Database error finding deadline events", e);
        }
    }

    /**
     * Get all active student account IDs eligible to receive notifications.
     */
    public ArrayList<Long> findAllStudentIds() {
        String sql = "SELECT id FROM students WHERE role = 'STUDENT'";
        ArrayList<Long> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return ids;

        } catch (SQLException e) {
            log.error("Failed to query student IDs: {}", e.getMessage());
            throw new RuntimeException("Database error finding student IDs", e);
        }
    }

    /**
     * Get all registered account IDs in the portal (students and other eligible users)
     * to receive new event announcements.
     */
    public ArrayList<Long> findAllUserIds() {
        String sql = "SELECT id FROM students ORDER BY id ASC";
        ArrayList<Long> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return ids;

        } catch (SQLException e) {
            log.error("Failed to query user IDs for announcement: {}", e.getMessage());
            throw new RuntimeException("Database error finding user IDs", e);
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        n.setStudentId(rs.getLong("student_id"));
        n.setEventId(rs.getLong("event_id"));
        n.setNotificationType(rs.getString("notification_type"));
        n.setMessage(rs.getString("message"));
        n.setRead(rs.getBoolean("is_read"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) n.setCreatedAt(createdAt.toLocalDateTime());

        n.setEventTitle(rs.getString("event_title"));
        Timestamp deadline = rs.getTimestamp("registration_deadline");
        if (deadline != null) n.setRegistrationDeadline(deadline.toLocalDateTime());

        return n;
    }
}

