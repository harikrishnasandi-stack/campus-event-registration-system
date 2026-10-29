package com.campus.events.controller;

import com.campus.events.model.Event;
import com.campus.events.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Event REST controller.
 *
 * GET  /api/events         → all published events, Merge Sorted by date (student + admin)
 * GET  /api/events/{id}    → single event detail
 * GET  /api/events/admin/all → all events regardless of status (admin only)
 * POST /api/events         → create event (admin only)
 * PUT  /api/events/{id}    → update event (admin only)
 * PUT  /api/events/{id}/publish  → publish  (admin only)
 * PUT  /api/events/{id}/cancel   → cancel   (admin only)
 * PUT  /api/events/{id}/complete → complete (admin only)
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    /** All published events — sorted by date using Merge Sort (O(n log n)). */
    @GetMapping
    public ResponseEntity<?> getPublishedEvents() {
        return ResponseEntity.ok(eventService.getPublishedEvents());
    }

    /** Single event by ID — uses HashMap cache in EventDAO. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(@PathVariable Long id) {
        Optional<Event> event = eventService.getEventById(id);
        return event.<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** All events (any status) — admin dashboard view. */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    /** Create a new event. adminId extracted from JWT principal. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createEvent(@RequestBody Event event, Authentication auth) {
        try {
            Long adminId = (Long) auth.getPrincipal();
            Event created = eventService.createEvent(event, adminId);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Update event fields (admin only). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event event) {
        try {
            Event updated = eventService.updateEvent(id, event);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Publish: DRAFT → PUBLISHED */
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> publishEvent(@PathVariable Long id) {
        try {
            Event updated = eventService.publishEvent(id);
            return ResponseEntity.ok(Map.of("message", "Event published successfully", "event", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Cancel event */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelEvent(@PathVariable Long id) {
        try {
            Event updated = eventService.cancelEvent(id);
            return ResponseEntity.ok(Map.of("message", "Event cancelled successfully", "event", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Mark event completed */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> completeEvent(@PathVariable Long id) {
        try {
            Event updated = eventService.completeEvent(id);
            return ResponseEntity.ok(Map.of("message", "Event marked as completed", "event", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Generic status update endpoint (PUBLISHED, CANCELLED, COMPLETED) */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status field is required"));
        }
        try {
            Event updated = switch (status.toUpperCase()) {
                case "CANCELLED" -> eventService.cancelEvent(id);
                case "PUBLISHED" -> eventService.publishEvent(id);
                case "COMPLETED" -> eventService.completeEvent(id);
                default -> throw new IllegalArgumentException("Invalid status: " + status);
            };
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}

