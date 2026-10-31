package com.campus.events;

import com.campus.events.dao.EventDAO;
import com.campus.events.dao.NotificationDAO;
import com.campus.events.model.Event;
import com.campus.events.model.Notification;
import com.campus.events.security.JwtUtil;
import com.campus.events.service.EventService;
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
    private EventService eventService;

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
    @DisplayName("Admin event creation automatically broadcasts notification to all registered users")
    void testEventCreationBroadcastsNotificationsToAllUsers() {
        Event newEvent = new Event();
        String uniqueTitle = "SPARKZ Symposium " + System.currentTimeMillis();
        newEvent.setTitle(uniqueTitle);
        newEvent.setDescription("Annual National Level Tech Symposium.");
        newEvent.setVenue("KARE Main Auditorium");
        newEvent.setEventDate(LocalDateTime.now().plusDays(10));
        newEvent.setRegistrationDeadline(LocalDateTime.now().plusDays(7));
        newEvent.setTotalSeats(200);

        // Admin creates event (Admin ID = 1)
        Event created = eventService.createEvent(newEvent, 1L);
        assertNotNull(created.getId());
        assertEquals("PUBLISHED", created.getStatus());

        // Verify student1 received the announcement notification
        ArrayList<Notification> notifs = notificationService.getStudentNotifications(student1Id);
        Notification announcement = notifs.stream()
                .filter(n -> n.getEventId().equals(created.getId()) && "EVENT_ANNOUNCEMENT".equals(n.getNotificationType()))
                .findFirst()
                .orElse(null);

        assertNotNull(announcement, "Student 1 should have received the event announcement notification");
        assertEquals("New Event Announcement!", announcement.getTitle());
        assertTrue(announcement.getMessage().contains(uniqueTitle));
        assertTrue(announcement.getMessage().contains("KARE Main Auditorium"));
        assertTrue(announcement.getMessage().contains("Log in to view event details and register"));
        assertFalse(announcement.isRead());
    }

    @Test
    @DisplayName("Event creation notification is idempotent on repeated broadcast")
    void testEventCreationNotificationIdempotency() {
        Event event = new Event();
        event.setTitle("Idempotency Fest " + System.currentTimeMillis());
        event.setVenue("Block 3 Lab");
        event.setDescription("Testing duplicate prevention");
        event.setEventDate(LocalDateTime.now().plusDays(5));
        event.setRegistrationDeadline(LocalDateTime.now().plusDays(3));
        event.setTotalSeats(50);
        event.setStatus("PUBLISHED");
        event.setCreatedBy(1L);

        Long eventId = eventDAO.save(event);
        event.setId(eventId);

        // 1. First broadcast
        int count1 = notificationService.broadcastNewEventAnnouncement(event);
        assertTrue(count1 > 0);

        // 2. Second broadcast for same event (must ignore duplicates)
        int count2 = notificationService.broadcastNewEventAnnouncement(event);
        assertEquals(0, count2, "Second run must be idempotent and create 0 duplicate rows");
    }

    @Test
    @DisplayName("Marking a notification as read decrements count and prevents double-decrement")
    void testMarkAsReadDecrementsUnreadCountAndPreventsDoubleDecrement() throws Exception {
        Notification n = new Notification(student1Id, 1L, "TEST_READ_" + System.currentTimeMillis(), "Read Test");
        notificationDAO.save(n);
        Long notifId = n.getId();
        assertNotNull(notifId);

        int initialUnread = notificationService.getUnreadCount(student1Id);

        // 1. Mark as read first time
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasUnread").value(true))
                .andExpect(jsonPath("$.unreadCount").value(initialUnread - 1));

        // 2. Mark as read second time (idempotent, does not decrement count again)
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasUnread").value(false))
                .andExpect(jsonPath("$.unreadCount").value(initialUnread - 1));
    }

    @Test
    @DisplayName("Mark all as read marks all student notifications as read and unread count is 0")
    void testMarkAllAsRead() throws Exception {
        // Ensure student has at least one unread notification
        Notification n = new Notification(student1Id, 1L, "TEST_ALL_" + System.currentTimeMillis(), "Mark All Test");
        notificationDAO.save(n);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));

        assertEquals(0, notificationService.getUnreadCount(student1Id));
    }

    @Test
    @DisplayName("Deadline notification processing with controlled test date and idempotency")
    void testDeadlineNotificationGenerationAndIdempotency() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        LocalDateTime deadline = targetDate.atTime(18, 0);

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
        cancelledEvent.setStatus("CANCELLED");
        cancelledEvent.setCreatedBy(1L);

        eventDAO.save(cancelledEvent);

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
        Notification n = new Notification(student1Id, 1L, "TEST_TYPE_" + System.currentTimeMillis(), "Test Message");
        notificationDAO.save(n);
        Long notifId = n.getId();
        assertNotNull(notifId);

        // Student 2 attempts to mark Student 1's notification as read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + student2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        // Student 1 can mark their own notification as read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification marked as read"));
    }
}
