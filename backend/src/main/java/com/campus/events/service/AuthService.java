package com.campus.events.service;

import com.campus.events.dao.StudentDAO;
import com.campus.events.model.Student;
import com.campus.events.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authentication service: login and student registration.
 *
 * Security design:
 *  - Passwords stored as BCrypt hashes (cost=12), never plaintext
 *  - JWT token issued on successful login
 *  - Generic "Invalid email or password" message prevents user enumeration
 *
 * Java Collections: HashMap used to build the JSON response map.
 */
@Service
public class AuthService {

    @Autowired private StudentDAO     studentDAO;
    @Autowired private JwtUtil        jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * Authenticate a user by email + password.
     *
     * @return HashMap with token, role, name, userId, etc.
     * @throws RuntimeException with generic message on failure (prevents user enumeration)
     */
    public Map<String, Object> login(String email, String password) {
        // 1. Look up account
        Optional<Student> opt = studentDAO.findByEmail(email);
        if (opt.isEmpty()) {
            throw new RuntimeException("Invalid email or password");
        }
        Student student = opt.get();

        // 2. Verify BCrypt hash
        if (!passwordEncoder.matches(password, student.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // 3. Issue JWT
        String token = jwtUtil.generateToken(student.getId(), student.getRole(), student.getName());

        // 4. Build response — Java Collections: HashMap
        Map<String, Object> resp = new HashMap<>();
        resp.put("token",          token);
        resp.put("role",           student.getRole());
        resp.put("name",           student.getName());
        resp.put("userId",         student.getId());
        resp.put("registerNumber", student.getRegisterNumber());
        resp.put("email",          student.getEmail());
        resp.put("department",     student.getDepartment());
        resp.put("year",           student.getYear());
        return resp;
    }

    /**
     * Register a new student account.
     * Hashes password with BCrypt before storing.
     *
     * @throws RuntimeException if email or register number is already taken
     */
    public Map<String, Object> registerStudent(String registerNumber, String name,
                                                String email, String password,
                                                String department, String year) {
        if (studentDAO.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email is already registered");
        }
        if (studentDAO.findByRegisterNumber(registerNumber).isPresent()) {
            throw new RuntimeException("Register number is already in use");
        }

        Student s = new Student();
        s.setRegisterNumber(registerNumber);
        s.setName(name);
        s.setEmail(email);
        s.setPasswordHash(passwordEncoder.encode(password)); // BCrypt hash
        s.setDepartment(department);
        s.setYear(year);
        s.setRole("STUDENT");

        Long id = studentDAO.save(s);

        String token = jwtUtil.generateToken(id, "STUDENT", name);

        Map<String, Object> resp = new HashMap<>();
        resp.put("token",          token);
        resp.put("role",           "STUDENT");
        resp.put("name",           name);
        resp.put("userId",         id);
        resp.put("registerNumber", registerNumber);
        resp.put("email",          email);
        resp.put("department",     department);
        resp.put("year",           year);
        return resp;
    }
}

