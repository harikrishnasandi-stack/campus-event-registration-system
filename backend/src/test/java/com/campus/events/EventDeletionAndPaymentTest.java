package com.campus.events;

import com.campus.events.dao.EventDAO;
import com.campus.events.dao.PaymentDAO;
import com.campus.events.dao.RegistrationDAO;
import com.campus.events.model.Event;
import com.campus.events.model.PaymentSubmission;
import com.campus.events.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class EventDeletionAndPaymentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventDAO eventDAO;

    @Autowired
    private RegistrationDAO registrationDAO;

    @Autowired
    private PaymentDAO paymentDAO;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generateToken(1L, "ADMIN", "Campus Admin");
        studentToken = jwtUtil.generateToken(2L, "STUDENT", "Arjun Sharma");
    }

    private byte[] createDummyPngImage() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Admin can delete a cancelled event without registrations (hard delete)")
    void testAdminHardDeleteEvent() throws Exception {
        Event ev = new Event();
        ev.setTitle("Hard Delete Candidate " + System.currentTimeMillis());
        ev.setDescription("To be deleted cleanly");
        ev.setVenue("Test Room 101");
        ev.setEventDate(LocalDateTime.now().plusDays(10));
        ev.setRegistrationDeadline(LocalDateTime.now().plusDays(8));
        ev.setTotalSeats(30);
        ev.setStatus("CANCELLED");
        ev.setCreatedBy(1L);
        Long eventId = eventDAO.save(ev);
        assertNotNull(eventId);

        mockMvc.perform(delete("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event deleted successfully"));

        // Verify hard deleted
        Optional<Event> found = eventDAO.findById(eventId);
        assertTrue(found.isEmpty(), "Event should be removed from database");
    }

    @Test
    @DisplayName("Student cannot delete an event (HTTP 403 Forbidden)")
    void testStudentCannotDeleteEvent() throws Exception {
        Event ev = new Event();
        ev.setTitle("Forbidden Delete Event " + System.currentTimeMillis());
        ev.setTotalSeats(20);
        ev.setStatus("CANCELLED");
        ev.setCreatedBy(1L);
        Long eventId = eventDAO.save(ev);
        assertNotNull(eventId);

        mockMvc.perform(delete("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cannot delete an active published event with registrations (requires cancellation first)")
    void testCannotDeleteActivePublishedEventWithRegistrations() throws Exception {
        Event ev = new Event();
        ev.setTitle("Active Published Event " + System.currentTimeMillis());
        ev.setTotalSeats(50);
        ev.setStatus("PUBLISHED");
        ev.setCreatedBy(1L);
        Long eventId = eventDAO.save(ev);
        assertNotNull(eventId);

        // Register student 2
        registrationDAO.registerStudent(2L, eventId);

        mockMvc.perform(delete("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot delete an active published event with registrations. Please cancel the event first."));
    }

    @Test
    @DisplayName("Deleting a cancelled event with registrations performs safe soft-delete/archiving")
    void testSafeSoftDeletePreservesHistoricalRegistrations() throws Exception {
        Event ev = new Event();
        ev.setTitle("Soft Delete Candidate " + System.currentTimeMillis());
        ev.setTotalSeats(50);
        ev.setStatus("CANCELLED");
        ev.setCreatedBy(1L);
        Long eventId = eventDAO.save(ev);
        assertNotNull(eventId);

        // Register student 2
        try {
            registrationDAO.registerStudent(2L, eventId);
        } catch (Exception ignored) {
            // Cancelled event registration might fail, so create a dummy registration directly
            registrationDAO.createOrUpdatePendingPaidRegistration(2L, eventId);
        }

        mockMvc.perform(delete("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event deleted successfully"));

        // Verify soft deleted in DB
        Event updated = eventDAO.findById(eventId).orElseThrow();
        assertTrue(updated.isDeleted(), "is_deleted flag must be true");

        // Verify not in published or admin active listings
        assertFalse(eventDAO.findAllPublished().stream().anyMatch(e -> e.getId().equals(eventId)));
        assertFalse(eventDAO.findAll().stream().anyMatch(e -> e.getId().equals(eventId)));
    }

    @Test
    @DisplayName("Paid event workflow: submit proof, reject duplicate txn, admin approve, view receipt")
    void testPaidEventFullWorkflow() throws Exception {
        // 1. Create paid event with ₹150 fee
        Event ev = new Event();
        ev.setTitle("Robotics Workshop 2026 " + System.currentTimeMillis());
        ev.setDescription("Hands-on robotics");
        ev.setVenue("Robotics Lab");
        ev.setEventDate(LocalDateTime.now().plusDays(15));
        ev.setRegistrationDeadline(LocalDateTime.now().plusDays(10));
        ev.setTotalSeats(40);
        ev.setStatus("PUBLISHED");
        ev.setFee(new BigDecimal("150.00"));
        ev.setUpiQrImage("sample-qr.png");
        ev.setCreatedBy(1L);
        Long eventId = eventDAO.save(ev);
        assertNotNull(eventId);

        byte[] imgBytes = createDummyPngImage();
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot", "payment_receipt.png", "image/png", imgBytes
        );

        String txnId = "UPI" + System.currentTimeMillis();

        // 2. Student submits payment proof
        mockMvc.perform(multipart("/api/payments/submit")
                        .file(screenshot)
                        .param("eventId", String.valueOf(eventId))
                        .param("transactionId", txnId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment proof submitted successfully. Your registration is awaiting admin verification."))
                .andExpect(jsonPath("$.submission.status").value("PENDING"));

        // 3. Submitting duplicate transaction ID should be rejected
        mockMvc.perform(multipart("/api/payments/submit")
                        .file(screenshot)
                        .param("eventId", String.valueOf(eventId))
                        .param("transactionId", txnId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This UPI transaction ID has already been submitted."));

        // 4. Admin counts should reflect pending
        mockMvc.perform(get("/api/payments/admin/counts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PENDING").isNumber());

        // Find payment ID
        PaymentSubmission sub = paymentDAO.findByTransactionId(txnId).orElseThrow();
        assertEquals("PENDING", sub.getStatus());

        // 5. Student cannot access admin payments list (403 Forbidden)
        mockMvc.perform(get("/api/payments/admin/all")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 6. Student cannot approve payment (403 Forbidden)
        mockMvc.perform(post("/api/payments/" + sub.getId() + "/approve")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 7. Admin approves payment
        mockMvc.perform(post("/api/payments/" + sub.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.status").value("APPROVED"));

        // Verify registration is now REGISTERED with APPROVED payment
        PaymentSubmission approvedSub = paymentDAO.findById(sub.getId()).orElseThrow();
        assertEquals("APPROVED", approvedSub.getStatus());

        // 8. Student can now view receipt
        mockMvc.perform(get("/api/payments/receipt/" + sub.getRegistrationId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.transactionId").value(txnId))
                .andExpect(jsonPath("$.amount").value(150.0));
    }

    @Test
    @DisplayName("Admin can reject payment with reason and notify student")
    void testAdminRejectPayment() throws Exception {
        Event ev = new Event();
        ev.setTitle("AI Conclave " + System.currentTimeMillis());
        ev.setEventDate(LocalDateTime.now().plusDays(12));
        ev.setRegistrationDeadline(LocalDateTime.now().plusDays(9));
        ev.setTotalSeats(30);
        ev.setStatus("PUBLISHED");
        ev.setFee(new BigDecimal("200.00"));
        ev.setUpiQrImage("qr.png");
        ev.setCreatedBy(1L);
        Long eventId = eventDAO.save(ev);
        assertNotNull(eventId);

        String txnId = "UPI_REJ_" + System.currentTimeMillis();
        MockMultipartFile file = new MockMultipartFile(
                "screenshot", "proof.png", "image/png", createDummyPngImage()
        );

        mockMvc.perform(multipart("/api/payments/submit")
                        .file(file)
                        .param("eventId", String.valueOf(eventId))
                        .param("transactionId", txnId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        PaymentSubmission sub = paymentDAO.findByTransactionId(txnId).orElseThrow();

        // Admin rejects without reason -> bad request
        mockMvc.perform(post("/api/payments/" + sub.getId() + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Admin rejects with reason
        mockMvc.perform(post("/api/payments/" + sub.getId() + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"UTR number does not match bank statement\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.status").value("REJECTED"))
                .andExpect(jsonPath("$.submission.rejectionReason").value("UTR number does not match bank statement"));

        // Student cannot get approved receipt for rejected payment
        mockMvc.perform(get("/api/payments/receipt/" + sub.getRegistrationId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());
    }
}
