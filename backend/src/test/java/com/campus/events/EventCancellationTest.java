package com.campus.events;

import com.campus.events.dao.EventDAO;
import com.campus.events.model.Event;
import com.campus.events.security.JwtUtil;
import com.campus.events.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class EventCancellationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventDAO eventDAO;

    @Autowired
    private EventService eventService;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;
    private String studentToken;
    private Long testEventId;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generateToken(1L, "ADMIN", "Campus Admin");
        studentToken = jwtUtil.generateToken(2L, "STUDENT", "Arjun Sharma");

        // Create a dedicated test event
        Event testEvent = new Event();
        testEvent.setTitle("JUnit Test Event " + System.currentTimeMillis());
        testEvent.setDescription("Event created for automated cancellation testing");
        testEvent.setVenue("Test Hall A");
        testEvent.setEventDate(LocalDateTime.now().plusDays(5));
        testEvent.setRegistrationDeadline(LocalDateTime.now().plusDays(3));
        testEvent.setTotalSeats(50);
        testEvent.setStatus("PUBLISHED");
        testEvent.setCreatedBy(1L);

        testEventId = eventDAO.save(testEvent);
        assertNotNull(testEventId, "Test event should be created");
    }

    @Test
    @DisplayName("Admin can successfully cancel a published event via PUT /api/events/{id}/cancel")
    void testAdminCanCancelEvent() throws Exception {
        mockMvc.perform(put("/api/events/" + testEventId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonExpect -> {
                    // Verify database status changed
                    Event updated = eventDAO.findById(testEventId).orElseThrow();
                    assertEquals("CANCELLED", updated.getStatus());
                });
    }

    @Test
    @DisplayName("Admin can cancel an event via generic status endpoint PUT /api/events/{id}/status")
    void testAdminCanCancelEventViaStatusEndpoint() throws Exception {
        mockMvc.perform(put("/api/events/" + testEventId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Event updated = eventDAO.findById(testEventId).orElseThrow();
        assertEquals("CANCELLED", updated.getStatus());
    }

    @Test
    @DisplayName("Authenticated student cannot cancel an event (HTTP 403 Forbidden)")
    void testStudentCannotCancelEvent() throws Exception {
        mockMvc.perform(put("/api/events/" + testEventId + "/cancel")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Verify status remained untouched in database
        Event event = eventDAO.findById(testEventId).orElseThrow();
        assertEquals("PUBLISHED", event.getStatus());
    }

    @Test
    @DisplayName("Unauthenticated request to cancel an event is rejected (HTTP 403 / 401)")
    void testUnauthenticatedCannotCancelEvent() throws Exception {
        mockMvc.perform(put("/api/events/" + testEventId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancelling non-existent event ID returns HTTP 404 Not Found")
    void testCancelInvalidEventIdReturns404() throws Exception {
        long nonExistentId = 999999L;
        mockMvc.perform(put("/api/events/" + nonExistentId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Cancelling an already-cancelled event returns HTTP 400 Bad Request")
    void testCancelAlreadyCancelledReturns400() throws Exception {
        // First cancellation
        eventService.cancelEvent(testEventId);

        // Second cancellation attempt
        mockMvc.perform(put("/api/events/" + testEventId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Event is already cancelled"));
    }
}

