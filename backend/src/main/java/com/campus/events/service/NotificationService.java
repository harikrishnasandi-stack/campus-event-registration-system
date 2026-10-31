package com.campus.events.service;

import com.campus.events.dao.NotificationDAO;
import com.campus.events.model.Event;
import com.campus.events.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing student notifications and deadline reminder processing.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final String TYPE_REGISTRATION_DEADLINE = "REGISTRATION_DEADLINE";
    public static final String TYPE_EVENT_ANNOUNCEMENT = "EVENT_ANNOUNCEMENT";
    public static final String TYPE_PAYMENT_SUBMITTED = "PAYMENT_SUBMITTED";
    public static final String TYPE_PAYMENT_APPROVED = "PAYMENT_APPROVED";
    public static final String TYPE_PAYMENT_REJECTED = "PAYMENT_REJECTED";

    @Autowired
    private NotificationDAO notificationDAO;

    @Value("${app.campus.timezone:Asia/Kolkata}")
    private String campusTimezone;

    /**
     * Broadcast a new event announcement notification to every registered account in the portal.
     * Notification title: "New Event Announcement!"
     * Message format: "A new event, [Event Title], has been announced! Date: [Event Date]. Venue: [Venue]. [Description] Log in to view event details and register."
     * Idempotent: Database UNIQUE KEY (student_id, event_id, notification_type) prevents duplicate notifications if retried.
     *
     * @param event the newly created event
     * @return count of notifications inserted
     */
    public int broadcastNewEventAnnouncement(Event event) {
        if (event == null || event.getId() == null) {
            log.warn("Cannot broadcast event announcement for null event or event without ID");
            return 0;
        }

        ArrayList<Long> userIds = notificationDAO.findAllUserIds();
        if (userIds.isEmpty()) {
            log.info("No registered users found to notify for event {}", event.getId());
            return 0;
        }

        String dateStr = event.getEventDate() != null
                ? event.getEventDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a"))
                : "TBA";
        String venueStr = (event.getVenue() != null && !event.getVenue().isBlank())
                ? event.getVenue().trim()
                : "TBA";
        String desc = (event.getDescription() != null && !event.getDescription().isBlank())
                ? event.getDescription().trim()
                : "";
        if (desc.length() > 100) {
            desc = desc.substring(0, 97) + "...";
        }
        String descPart = !desc.isEmpty() ? " " + desc : "";

        String message = String.format(
                "A new event, %s, has been announced! Date: %s. Venue: %s.%s Log in to view event details and register.",
                event.getTitle(),
                dateStr,
                venueStr,
                descPart
        );

        List<Notification> toCreate = new ArrayList<>();
        for (Long userId : userIds) {
            Notification n = new Notification(userId, event.getId(), TYPE_EVENT_ANNOUNCEMENT, message);
            toCreate.add(n);
        }

        int created = notificationDAO.saveAll(toCreate);
        log.info("Broadcasted {} new event announcement notifications for event ID {} ('{}') across {} accounts",
                created, event.getId(), event.getTitle(), userIds.size());
        return created;
    }

    /**
     * Send a single payment-related notification to a student.
     * Uses save() in NotificationDAO.
     */
    public boolean sendPaymentNotification(Long studentId, Long eventId, String type, String message) {
        Notification n = new Notification(studentId, eventId, type, message);
        return notificationDAO.save(n);
    }

    /**
     * Identify events whose registration deadline is tomorrow in the configured campus timezone,
     * and create persistent in-app notifications for eligible students.
     * Idempotent: Database UNIQUE KEY (student_id, event_id, notification_type) prevents duplicates.
     *
     * @return count of new notifications created
     */
    public int processDeadlineNotifications() {
        ZoneId zone = ZoneId.of(campusTimezone);
        ZonedDateTime nowInCampus = ZonedDateTime.now(zone);
        LocalDate tomorrow = nowInCampus.toLocalDate().plusDays(1);
        return processDeadlineNotificationsForDate(tomorrow);
    }

    /**
     * Process deadline notifications for a target calendar day.
     * Enables deterministic testing with controlled test dates/clocks without waiting.
     *
     * @param targetTomorrow the calendar day to check for registration deadlines
     * @return count of new notifications created
     */
    public int processDeadlineNotificationsForDate(LocalDate targetTomorrow) {
        LocalDateTime startOfTarget = targetTomorrow.atStartOfDay();
        LocalDateTime endOfTarget = targetTomorrow.plusDays(1).atStartOfDay();

        log.info("Checking events with registration deadline between {} and {} (target day: {})",
                startOfTarget, endOfTarget, targetTomorrow);

        // 1. Fetch published, non-cancelled events whose registration deadline is on target date
        ArrayList<Event> deadlineEvents = notificationDAO.findEligibleDeadlineEvents(startOfTarget, endOfTarget);
        if (deadlineEvents.isEmpty()) {
            log.info("No published events found with registration deadline on {}", targetTomorrow);
            return 0;
        }

        // 2. Fetch all registered students
        ArrayList<Long> studentIds = notificationDAO.findAllStudentIds();
        if (studentIds.isEmpty()) {
            log.info("No active student accounts found to notify");
            return 0;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        String formattedDate = targetTomorrow.format(dateFmt);

        // 3. Build notification items
        List<Notification> toCreate = new ArrayList<>();
        for (Event event : deadlineEvents) {
            String message = String.format(
                    "Reminder: Registration for [%s] closes tomorrow, %s. Register before the deadline.",
                    event.getTitle(),
                    formattedDate
            );

            for (Long studentId : studentIds) {
                Notification n = new Notification(studentId, event.getId(), TYPE_REGISTRATION_DEADLINE, message);
                toCreate.add(n);
            }
        }

        // 4. Batch insert with idempotency
        int created = notificationDAO.saveAll(toCreate);
        log.info("Generated {} new deadline notifications for {} event(s) across {} student(s)",
                created, deadlineEvents.size(), studentIds.size());
        return created;
    }

    /** Get notifications for a student, newest first. */
    public ArrayList<Notification> getStudentNotifications(Long studentId) {
        return notificationDAO.findByStudentId(studentId);
    }

    /** Get count of unread notifications for a student. */
    public int getUnreadCount(Long studentId) {
        return notificationDAO.countUnreadByStudentId(studentId);
    }

    /** Mark a single notification as read, enforcing that it belongs to studentId. */
    public Optional<Boolean> markAsRead(Long id, Long studentId) {
        return notificationDAO.markAsRead(id, studentId);
    }

    /** Mark all notifications for student as read. */
    public int markAllAsRead(Long studentId) {
        return notificationDAO.markAllAsRead(studentId);
    }
}

