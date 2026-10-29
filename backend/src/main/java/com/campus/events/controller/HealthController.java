package com.campus.events.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check endpoint — used to verify the backend is running.
 * Public endpoint: no auth required (see SecurityConfig).
 *
 * GET /api/health → { "status": "UP", ... }
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status",    "UP",
                "service",   "Campus Event Management System",
                "timestamp", LocalDateTime.now().toString(),
                "version",   "1.0.0"
        ));
    }
}

