package com.campus.events.dao;

import com.campus.events.model.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Data Access Object for Certificates table.
 * Uses raw JDBC with PreparedStatement — no JPA/Hibernate.
 *
 * Java Collections used: ArrayList for result lists.
 */
@Repository
public class CertificateDAO {

    private static final Logger log = LoggerFactory.getLogger(CertificateDAO.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Save a new certificate record. Uses ON DUPLICATE KEY UPDATE
     * so re-uploading a cert for the same student+event replaces the old file.
     * Returns generated ID.
     */
    public Long save(Certificate cert) {
        String sql = """
                INSERT INTO certificates (student_id, event_id, file_name, stored_file_name, file_size, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    file_name        = VALUES(file_name),
                    stored_file_name = VALUES(stored_file_name),
                    file_size        = VALUES(file_size),
                    uploaded_by      = VALUES(uploaded_by),
                    uploaded_at      = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, cert.getStudentId());
            ps.setLong(2, cert.getEventId());
            ps.setString(3, cert.getFileName());
            ps.setString(4, cert.getStoredFileName());
            ps.setLong(5, cert.getFileSize());
            ps.setLong(6, cert.getUploadedBy());

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);

        } catch (SQLException e) {
            log.error("Error saving certificate: {}", e.getMessage());
            throw new RuntimeException("Failed to save certificate: " + e.getMessage());
        }
        return null;
    }

    /** Find certificate for a specific student+event pair. */
    public Optional<Certificate> findByStudentAndEvent(Long studentId, Long eventId) {
        String sql = """
                SELECT c.*, s.name as student_name, s.register_number, e.title as event_title
                FROM certificates c
                JOIN students s ON c.student_id = s.id
                JOIN events e   ON c.event_id   = e.id
                WHERE c.student_id = ? AND c.event_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            ps.setLong(2, eventId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));

        } catch (SQLException e) {
            log.error("Error finding certificate for student {} event {}: {}", studentId, eventId, e.getMessage());
        }
        return Optional.empty();
    }

    /** Get all certificates for a student. Returns ArrayList. */
    public ArrayList<Certificate> findByStudentId(Long studentId) {
        String sql = """
                SELECT c.*, s.name as student_name, s.register_number, e.title as event_title
                FROM certificates c
                JOIN students s ON c.student_id = s.id
                JOIN events e   ON c.event_id   = e.id
                WHERE c.student_id = ?
                ORDER BY c.uploaded_at DESC
                """;
        ArrayList<Certificate> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            log.error("Error finding certificates for student {}: {}", studentId, e.getMessage());
        }
        return list;
    }

    /** Get all certificates for an event (admin view). Returns ArrayList. */
    public ArrayList<Certificate> findByEventId(Long eventId) {
        String sql = """
                SELECT c.*, s.name as student_name, s.register_number, e.title as event_title
                FROM certificates c
                JOIN students s ON c.student_id = s.id
                JOIN events e   ON c.event_id   = e.id
                WHERE c.event_id = ?
                ORDER BY s.name
                """;
        ArrayList<Certificate> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, eventId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            log.error("Error finding certificates for event {}: {}", eventId, e.getMessage());
        }
        return list;
    }

    /** Get all certificates (admin view). Returns ArrayList. */
    public ArrayList<Certificate> findAll() {
        String sql = """
                SELECT c.*, s.name as student_name, s.register_number, e.title as event_title
                FROM certificates c
                JOIN students s ON c.student_id = s.id
                JOIN events e   ON c.event_id   = e.id
                ORDER BY c.uploaded_at DESC
                """;
        ArrayList<Certificate> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            log.error("Error finding all certificates: {}", e.getMessage());
        }
        return list;
    }

    private Certificate mapRow(ResultSet rs) throws SQLException {
        Certificate c = new Certificate();
        c.setId(rs.getLong("id"));
        c.setStudentId(rs.getLong("student_id"));
        c.setEventId(rs.getLong("event_id"));
        c.setFileName(rs.getString("file_name"));
        c.setStoredFileName(rs.getString("stored_file_name"));
        c.setFileSize(rs.getLong("file_size"));
        c.setUploadedBy(rs.getLong("uploaded_by"));

        Timestamp ts = rs.getTimestamp("uploaded_at");
        if (ts != null) c.setUploadedAt(ts.toLocalDateTime());

        try { c.setStudentName(rs.getString("student_name")); }   catch (SQLException ignored) {}
        try { c.setRegisterNumber(rs.getString("register_number")); } catch (SQLException ignored) {}
        try { c.setEventTitle(rs.getString("event_title")); }     catch (SQLException ignored) {}

        return c;
    }
}

