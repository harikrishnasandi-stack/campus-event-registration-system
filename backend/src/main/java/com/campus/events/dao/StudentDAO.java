package com.campus.events.dao;

import com.campus.events.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Student table.
 * Uses raw JDBC with PreparedStatement — no JPA/Hibernate.
 *
 * Java Collections used:
 *   - ArrayList to hold multiple Student results
 *   - Optional for nullable single-record lookups
 */
@Repository
public class StudentDAO {

    private static final Logger log = LoggerFactory.getLogger(StudentDAO.class);

    @Autowired
    private DataSource dataSource;

    /** Find a student by their email address (for login). */
    public Optional<Student> findByEmail(String email) {
        String sql = "SELECT * FROM students WHERE email = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error finding student by email: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Find a student by their register number. */
    public Optional<Student> findByRegisterNumber(String registerNumber) {
        String sql = "SELECT * FROM students WHERE register_number = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, registerNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error finding student by register number: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Find a student by their ID. */
    public Optional<Student> findById(Long id) {
        String sql = "SELECT * FROM students WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error finding student by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Retrieve all students (for admin participant list). Returns ArrayList. */
    public ArrayList<Student> findAll() {
        String sql = "SELECT * FROM students WHERE role = 'STUDENT' ORDER BY name";
        ArrayList<Student> students = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                students.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error fetching all students: {}", e.getMessage());
        }
        return students;
    }

    /** Insert a new student record. Returns generated ID. */
    public Long save(Student student) {
        String sql = """
                INSERT INTO students (register_number, name, email, password_hash, department, year, role)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, student.getRegisterNumber());
            ps.setString(2, student.getName());
            ps.setString(3, student.getEmail());
            ps.setString(4, student.getPasswordHash());
            ps.setString(5, student.getDepartment());
            ps.setString(6, student.getYear());
            ps.setString(7, student.getRole() != null ? student.getRole() : "STUDENT");

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);

        } catch (SQLException e) {
            log.error("Error saving student: {}", e.getMessage());
            throw new RuntimeException("Failed to save student: " + e.getMessage());
        }
        return null;
    }

    /** Map a ResultSet row to a Student object. */
    private Student mapRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getLong("id"));
        s.setRegisterNumber(rs.getString("register_number"));
        s.setName(rs.getString("name"));
        s.setEmail(rs.getString("email"));
        s.setPasswordHash(rs.getString("password_hash"));
        s.setDepartment(rs.getString("department"));
        s.setYear(rs.getString("year"));
        s.setRole(rs.getString("role"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) s.setCreatedAt(ts.toLocalDateTime());
        return s;
    }
}
