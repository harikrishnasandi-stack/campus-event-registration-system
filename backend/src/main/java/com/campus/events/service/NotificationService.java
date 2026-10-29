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

    @Autowired
    private NotificationDAO notificationDAO;

    @Value("${app.campus.timezone:Asia/Kolkata}")
    private String campusTimezone;

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

