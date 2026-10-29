package com.campus.events.controller;

import com.campus.events.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Registration REST controller.
 *
 * POST /api/registrations/event/{eventId}  → student registers (own ID from JWT)
 * GET  /api/registrations/my              → student's own registrations
 * GET  /api/registrations/event/{eventId} → all participants (admin only)
 *
 * Security: studentId is NEVER taken from the request body.
 * It is always extracted from the validated JWT token principal.
 * This prevents a student from registering as a different student.
 */
@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    /**
     * Register authenticated student for an event.
     * studentId = JWT principal (cannot be spoofed by client).
     */
    @PostMapping("/event/{eventId}")
    public ResponseEntity<?> register(@PathVariable Long eventId, Authentication auth) {
        try {
            Long studentId = (Long) auth.getPrincipal();
            Map<String, Object> result = registrationService.register(studentId, eventId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get own registrations.
     * studentId from JWT — student cannot view another student's registrations.
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyRegistrations(Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(registrationService.getStudentRegistrations(studentId));
    }

    /**
     * Get all registrations for an event (admin only).
     * Returns full participant list including name, register number, department, etc.
     */
    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getEventRegistrations(@PathVariable Long eventId) {
        return ResponseEntity.ok(registrationService.getEventRegistrations(eventId));
    }
}

