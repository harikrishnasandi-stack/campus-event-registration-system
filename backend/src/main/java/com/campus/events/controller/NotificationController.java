package com.campus.events.controller;

import com.campus.events.model.Notification;
import com.campus.events.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for student notifications and deadline reminders.
 *
 * GET   /api/notifications              → list authenticated student's notifications
 * GET   /api/notifications/unread-count → unread count for current student
 * PATCH /api/notifications/{id}/read    → mark a specific notification as read
 * PATCH /api/notifications/read-all     → mark all student's notifications as read
 * POST  /api/notifications/trigger-check → manual trigger for deadline check (Admin / testing)
 *
 * Security: studentId is derived from the authenticated SecurityContext principal,
 * preventing any student from querying or updating another student's notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * Get all notifications for the authenticated student.
     */
    @GetMapping
    public ResponseEntity<ArrayList<Notification>> getMyNotifications(Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(notificationService.getStudentNotifications(studentId));
    }

    /**
     * Get unread notification count for the authenticated student.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        int count = notificationService.getUnreadCount(studentId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * Mark a single notification as read.
     * Enforces that the notification belongs to the authenticated student.
     * Returns whether it was previously unread, and the updated unread count.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        Optional<Boolean> result = notificationService.markAsRead(id, studentId);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Notification not found or access denied"));
        }
        boolean wasUnread = result.get();
        int remainingUnread = notificationService.getUnreadCount(studentId);
        return ResponseEntity.ok(Map.of(
                "message", "Notification marked as read",
                "id", id,
                "wasUnread", wasUnread,
                "unreadCount", remainingUnread
        ));
    }

    /**
     * Mark all notifications for the authenticated student as read.
     * Returns total updated count and confirmed unreadCount = 0.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        int count = notificationService.markAllAsRead(studentId);
        return ResponseEntity.ok(Map.of(
                "message", "All notifications marked as read",
                "updatedCount", count,
                "unreadCount", 0
        ));
    }

    /**
     * Admin/Testing endpoint to manually trigger the deadline notification check.
     * Optionally accepts a ?targetDate=YYYY-MM-DD parameter to test controlled dates.
     */
    @PostMapping("/trigger-check")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> triggerCheck(
            @RequestParam(value = "targetDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        int created;
        if (targetDate != null) {
            created = notificationService.processDeadlineNotificationsForDate(targetDate);
        } else {
            created = notificationService.processDeadlineNotifications();
        }

        return ResponseEntity.ok(Map.of(
                "message", "Deadline notification check completed",
                "notificationsCreated", created,
                "targetDate", targetDate != null ? targetDate.toString() : "tomorrow"
        ));
    }
}

