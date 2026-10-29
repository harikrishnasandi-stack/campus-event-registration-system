package com.campus.events;

import com.campus.events.dao.EventDAO;
import com.campus.events.dao.NotificationDAO;
import com.campus.events.model.Event;
import com.campus.events.model.Notification;
import com.campus.events.security.JwtUtil;
import com.campus.events.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class NotificationServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDAO notificationDAO;

    @Autowired
    private EventDAO eventDAO;

    @Autowired
    private JwtUtil jwtUtil;

    private String student1Token;
    private String student2Token;
    private Long student1Id = 2L; // Arjun Sharma
    private Long student2Id = 9999L; // Imaginary second student for cross-user security test

    @BeforeEach
    void setUp() {
        student1Token = jwtUtil.generateToken(student1Id, "STUDENT", "Arjun Sharma");
        student2Token = jwtUtil.generateToken(student2Id, "STUDENT", "Other Student");
    }

    @Test
    @DisplayName("Deadline notification processing with controlled test date and idempotency")
    void testDeadlineNotificationGenerationAndIdempotency() {
        // Create an event with registration deadline specifically set to target date
        LocalDate targetDate = LocalDate.now().plusDays(2);
        LocalDateTime deadline = targetDate.atTime(18, 0); // 6:00 PM on target date

        Event testEvent = new Event();
        testEvent.setTitle("Deadline Test Symposium " + System.currentTimeMillis());
        testEvent.setDescription("Testing deadline reminders");
        testEvent.setVenue("Tech Lab 1");
        testEvent.setEventDate(targetDate.plusDays(3).atTime(10, 0));
        testEvent.setRegistrationDeadline(deadline);
        testEvent.setTotalSeats(100);
        testEvent.setStatus("PUBLISHED");
        testEvent.setCreatedBy(1L);

        Long eventId = eventDAO.save(testEvent);
        assertNotNull(eventId);

        // 1. Process deadline notifications for targetDate
        int firstRunCreated = notificationService.processDeadlineNotificationsForDate(targetDate);
        assertTrue(firstRunCreated > 0, "Should generate notifications for eligible students");

        // 2. Process again immediately with the same targetDate (Idempotency test)
        int secondRunCreated = notificationService.processDeadlineNotificationsForDate(targetDate);
        assertEquals(0, secondRunCreated, "Second run must be idempotent and create 0 duplicates");

        // 3. Verify student1 received the notification
        ArrayList<Notification> notifs = notificationService.getStudentNotifications(student1Id);
        boolean found = notifs.stream().anyMatch(n -> n.getEventId().equals(eventId));
        assertTrue(found, "Student should have a notification for the deadline event");
    }

    @Test
    @DisplayName("Cancelled events do not trigger deadline notifications")
    void testCancelledEventsExcludedFromNotifications() {
        LocalDate targetDate = LocalDate.now().plusDays(4);

        Event cancelledEvent = new Event();
        cancelledEvent.setTitle("Cancelled Fest " + System.currentTimeMillis());
        cancelledEvent.setVenue("Auditorium");
        cancelledEvent.setEventDate(targetDate.plusDays(2).atTime(10, 0));
        cancelledEvent.setRegistrationDeadline(targetDate.atTime(15, 0));
        cancelledEvent.setTotalSeats(100);
        cancelledEvent.setStatus("CANCELLED"); // Cancelled!
        cancelledEvent.setCreatedBy(1L);

        Long eventId = eventDAO.save(cancelledEvent);

        int created = notificationService.processDeadlineNotificationsForDate(targetDate);
        assertEquals(0, created, "Cancelled events must not trigger any notifications");
    }

    @Test
    @DisplayName("Student can view own notifications and unread count via REST endpoints")
    void testStudentCanFetchOwnNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber());
    }

    @Test
    @DisplayName("Student A cannot mark Student B's notification as read (Cross-student isolation)")
    void testStudentCannotMarkOthersNotificationAsRead() throws Exception {
        // Create a notification explicitly owned by student 1
        Notification n = new Notification(student1Id, 1L, "TEST_TYPE_" + System.currentTimeMillis(), "Test Message");
        notificationDAO.save(n);
        Long notifId = n.getId();
        assertNotNull(notifId);

        // Student 2 attempts to mark Student 1's notification as read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + student2Token))
                .andExpect(status().isNotFound()) // Query matches both ID and student_id; returns 404 access denied
                .andExpect(jsonPath("$.error").exists());

        // Student 1 can mark their own notification as read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification marked as read"));
    }
}

