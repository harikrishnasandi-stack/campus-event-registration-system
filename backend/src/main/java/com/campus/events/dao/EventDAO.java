package com.campus.events.dao;

import com.campus.events.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
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

    /** Retrieve all published events (for students). Returns ArrayList. */
    public ArrayList<Event> findAllPublished() {
        String sql = "SELECT * FROM events WHERE status = 'PUBLISHED' ORDER BY event_date ASC";
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

    /** Retrieve all events (for admin). Returns ArrayList. */
    public ArrayList<Event> findAll() {
        String sql = "SELECT * FROM events ORDER BY event_date DESC";
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
                                    registration_deadline, total_seats, registered_count, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
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
                event_date=?, registration_deadline=?, total_seats=?, status=?, updated_at=NOW()
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
            ps.setLong(9, event.getId());

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

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) e.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) e.setUpdatedAt(updatedAt.toLocalDateTime());

        return e;
    }
}
