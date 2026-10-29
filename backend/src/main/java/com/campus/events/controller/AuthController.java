package com.campus.events.controller;

import com.campus.events.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication REST controller.
 * All endpoints here are PUBLIC (no JWT required) — see SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * POST /api/auth/login
     * Body: { "email": "...", "password": "..." }
     * Returns JWT token + user profile on success.
     * Returns 401 on invalid credentials.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String email    = body.get("email");
            String password = body.get("password");

            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email and password are required"));
            }

            Map<String, Object> result = authService.login(email.trim().toLowerCase(), password);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            // Generic message — do not reveal whether email exists
            return ResponseEntity.status(401)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/auth/register
     * Body: { "registerNumber", "name", "email", "password", "department", "year" }
     * Creates a new STUDENT account. Returns JWT + profile.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            String registerNumber = body.get("registerNumber");
            String name           = body.get("name");
            String email          = body.get("email");
            String password       = body.get("password");
            String department     = body.getOrDefault("department", "");
            String year           = body.getOrDefault("year", "");

            if (registerNumber == null || name == null || email == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "registerNumber, name, email and password are required"));
            }
            if (password.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password must be at least 8 characters"));
            }

            Map<String, Object> result = authService.registerStudent(
                    registerNumber.trim(), name.trim(),
                    email.trim().toLowerCase(), password,
                    department, year);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

