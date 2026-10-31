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

    /**
     * Delete an event (Admin only).
     * Requirements:
     * - Available for cancelled events.
     * - Prevents accidental deletion of published events with active registrations (requires cancellation first).
     * - Safe archiving / deletion preserving historical registration & payment records.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        try {
            boolean ok = eventService.deleteEvent(id);
            if (ok) {
                return ResponseEntity.ok(Map.of("message", "Event deleted successfully", "id", id));
            } else {
                return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete event"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Upload UPI QR code image for an event (Admin only). */
    @PostMapping("/{id}/qr-code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadQrCode(@PathVariable Long id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            Optional<Event> opt = eventService.getEventById(id);
            if (opt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Event not found with ID: " + id));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please select a valid QR code image"));
            }
            if (file.getSize() > 5L * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "QR code image exceeds maximum allowed size of 5 MB"));
            }

            String ct = file.getContentType();
            if (ct == null || (!ct.equalsIgnoreCase("image/png") && !ct.equalsIgnoreCase("image/jpeg") && !ct.equalsIgnoreCase("image/jpg"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only PNG and JPG/JPEG images are allowed"));
            }

            String orig = file.getOriginalFilename();
            if (orig == null || (!orig.toLowerCase().endsWith(".png") && !orig.toLowerCase().endsWith(".jpg") && !orig.toLowerCase().endsWith(".jpeg"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "File must have .png, .jpg, or .jpeg extension"));
            }

            // Verify actual image content
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.getInputStream());
                if (img == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid image content uploaded"));
                }
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is not a valid image"));
            }

            String safeName = java.nio.file.Paths.get(orig).getFileName().toString().replaceAll("[^a-zA-Z0-9._\\-]", "_");
            String storedFileName = java.util.UUID.randomUUID() + "_" + safeName;

            java.nio.file.Path qrDir = resolveQrDir();
            java.nio.file.Files.createDirectories(qrDir);

            java.nio.file.Path target = qrDir.resolve(storedFileName).normalize();
            if (!target.startsWith(qrDir)) {
                return ResponseEntity.status(400).body(Map.of("error", "Path traversal detected"));
            }

            java.nio.file.Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            eventService.updateQrImage(id, storedFileName);
            return ResponseEntity.ok(Map.of(
                    "message", "UPI QR code uploaded successfully",
                    "qrImage", storedFileName,
                    "qrUrl", "/api/events/" + id + "/qr-code"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload QR code: " + e.getMessage()));
        }
    }

    /** Remove UPI QR code image from an event (Admin only). */
    @DeleteMapping("/{id}/qr-code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removeQrCode(@PathVariable Long id) {
        try {
            Optional<Event> opt = eventService.getEventById(id);
            if (opt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Event not found with ID: " + id));
            }
            Event event = opt.get();
            if (event.getFee().compareTo(java.math.BigDecimal.ZERO) > 0 && "PUBLISHED".equalsIgnoreCase(event.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot remove QR code from a published paid event. Please cancel or update the event fee first."));
            }
            eventService.updateQrImage(id, null);
            return ResponseEntity.ok(Map.of("message", "UPI QR code removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Serve UPI QR code image for viewing by students and admins. */
    @GetMapping("/{id}/qr-code")
    public ResponseEntity<?> getQrCode(@PathVariable Long id) {
        try {
            Optional<Event> opt = eventService.getEventById(id);
            if (opt.isEmpty() || opt.get().getUpiQrImage() == null || opt.get().getUpiQrImage().isBlank()) {
                return ResponseEntity.notFound().build();
            }

            String storedFileName = opt.get().getUpiQrImage();
            java.nio.file.Path target = findQrCodeFile(storedFileName);

            if (target == null || !java.nio.file.Files.exists(target)) {
                return ResponseEntity.notFound().build();
            }

            String contentType = storedFileName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            byte[] bytes = java.nio.file.Files.readAllBytes(target);

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                    .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=120, must-revalidate")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private java.nio.file.Path resolveQrDir() {
        java.nio.file.Path p1 = java.nio.file.Paths.get("./uploads/qr-codes").toAbsolutePath().normalize();
        if (java.nio.file.Files.exists(p1)) return p1;
        java.nio.file.Path p2 = java.nio.file.Paths.get("./backend/uploads/qr-codes").toAbsolutePath().normalize();
        if (java.nio.file.Files.exists(p2)) return p2;
        return p1;
    }

    private java.nio.file.Path findQrCodeFile(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) return null;
        java.nio.file.Path p1 = java.nio.file.Paths.get("./uploads/qr-codes").toAbsolutePath().normalize().resolve(storedFileName).normalize();
        if (p1.startsWith(java.nio.file.Paths.get("./uploads/qr-codes").toAbsolutePath().normalize()) && java.nio.file.Files.exists(p1)) {
            return p1;
        }
        java.nio.file.Path p2 = java.nio.file.Paths.get("./backend/uploads/qr-codes").toAbsolutePath().normalize().resolve(storedFileName).normalize();
        if (p2.startsWith(java.nio.file.Paths.get("./backend/uploads/qr-codes").toAbsolutePath().normalize()) && java.nio.file.Files.exists(p2)) {
            return p2;
        }
        return null;
    }
}

