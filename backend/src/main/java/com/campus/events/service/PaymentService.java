package com.campus.events.service;

import com.campus.events.dao.EventDAO;
import com.campus.events.dao.PaymentDAO;
import com.campus.events.dao.RegistrationDAO;
import com.campus.events.model.Event;
import com.campus.events.model.PaymentSubmission;
import com.campus.events.model.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service managing manual UPI payment submissions, admin verification, and receipts.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB

    @Autowired
    private PaymentDAO paymentDAO;

    @Autowired
    private RegistrationDAO registrationDAO;

    @Autowired
    private EventDAO eventDAO;

    @Autowired
    private NotificationService notificationService;

    /**
     * Student submits UPI payment proof for a paid event.
     */
    public PaymentSubmission submitPaymentProof(Long studentId, Long eventId, String transactionId, MultipartFile file) throws IOException {
        // 1. Transaction ID validation
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("UPI Transaction ID / UTR Number is required");
        }
        String cleanTxnId = transactionId.trim();
        if (cleanTxnId.length() > 100) {
            throw new IllegalArgumentException("Transaction ID cannot exceed 100 characters");
        }

        // 2. File validation
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Payment screenshot / receipt is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Payment screenshot exceeds maximum allowed size of 5 MB");
        }

        String ct = file.getContentType();
        if (ct == null || (!ct.equalsIgnoreCase("image/png") && !ct.equalsIgnoreCase("image/jpeg") && !ct.equalsIgnoreCase("image/jpg"))) {
            throw new IllegalArgumentException("Only PNG and JPG/JPEG images are allowed");
        }

        String orig = file.getOriginalFilename();
        if (orig == null || (!orig.toLowerCase().endsWith(".png") && !orig.toLowerCase().endsWith(".jpg") && !orig.toLowerCase().endsWith(".jpeg"))) {
            throw new IllegalArgumentException("File must have a .png, .jpg, or .jpeg extension");
        }

        // Deep content validation (magic bytes & image decoding)
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new IllegalArgumentException("Uploaded file does not contain valid image data");
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid image file: " + ex.getMessage());
        }

        // 3. Event validation
        Optional<Event> eventOpt = eventDAO.findById(eventId);
        if (eventOpt.isEmpty() || eventOpt.get().isDeleted()) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        Event event = eventOpt.get();

        if (!"PUBLISHED".equalsIgnoreCase(event.getStatus())) {
            throw new IllegalStateException("Event is not open for registration (status: " + event.getStatus() + ")");
        }
        if (event.getRegistrationDeadline() != null && event.getRegistrationDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Registration deadline for this event has passed");
        }
        if (event.getFee().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("This is a free event. Please register directly without payment proof.");
        }
        if (event.getRegisteredCount() >= event.getTotalSeats()) {
            throw new IllegalStateException("All seats for this event are currently booked");
        }

        // 4. Check duplicate transaction ID across all payments
        Optional<PaymentSubmission> existingTxn = paymentDAO.findByTransactionId(cleanTxnId);
        if (existingTxn.isPresent()) {
            throw new IllegalStateException("This UPI transaction ID has already been submitted.");
        }

        // Check if student already has a pending or approved submission for this event
        Optional<PaymentSubmission> existingStudentSub = paymentDAO.findByStudentAndEvent(studentId, eventId);
        if (existingStudentSub.isPresent()) {
            if ("PENDING".equalsIgnoreCase(existingStudentSub.get().getStatus())) {
                throw new IllegalStateException("You already have a pending payment proof submission awaiting admin review.");
            }
            if ("APPROVED".equalsIgnoreCase(existingStudentSub.get().getStatus())) {
                throw new IllegalStateException("You already have an approved registration for this event.");
            }
        }

        // 5. Store image securely
        String safeName = Paths.get(orig).getFileName().toString().replaceAll("[^a-zA-Z0-9._\\-]", "_");
        String storedFileName = UUID.randomUUID() + "_" + safeName;

        Path paymentDir = Paths.get("./uploads/payments").toAbsolutePath().normalize();
        Files.createDirectories(paymentDir);

        Path target = paymentDir.resolve(storedFileName).normalize();
        if (!target.startsWith(paymentDir)) {
            throw new SecurityException("Path traversal detected — upload rejected");
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved payment proof image -> {} for student {} event {}", storedFileName, studentId, eventId);

        // 6. Create or update pending registration
        Long regId = registrationDAO.createOrUpdatePendingPaidRegistration(studentId, eventId);

        // 7. Save payment submission
        PaymentSubmission submission = new PaymentSubmission();
        submission.setRegistrationId(regId);
        submission.setStudentId(studentId);
        submission.setEventId(eventId);
        submission.setAmount(event.getFee());
        submission.setTransactionId(cleanTxnId);
        submission.setScreenshotPath(storedFileName);
        submission.setStatus("PENDING");
        submission.setSubmittedAt(LocalDateTime.now());

        Long paymentId = paymentDAO.save(submission);
        submission.setId(paymentId);

        // 8. Send notification to student
        try {
            String notifMsg = String.format(
                    "Payment proof for \"%s\" submitted successfully (Txn ID: %s, Amount: ₹%s). Your registration is awaiting admin verification.",
                    event.getTitle(), cleanTxnId, event.getFee()
            );
            notificationService.sendPaymentNotification(studentId, eventId, NotificationService.TYPE_PAYMENT_SUBMITTED, notifMsg);
        } catch (Exception e) {
            log.error("Failed to send payment submitted notification: {}", e.getMessage());
        }

        return submission;
    }

    /**
     * Admin approves payment submission.
     */
    public PaymentSubmission approvePayment(Long paymentId, Long adminId) {
        PaymentSubmission approved = paymentDAO.approvePayment(paymentId, adminId);

        // Notify student that payment is approved and registration confirmed
        try {
            String msg = String.format(
                    "Your payment of ₹%s for \"%s\" has been approved! Your registration is now confirmed. You can now view and download your registration receipt.",
                    approved.getAmount(), approved.getEventTitle()
            );
            notificationService.sendPaymentNotification(approved.getStudentId(), approved.getEventId(),
                    NotificationService.TYPE_PAYMENT_APPROVED, msg);
        } catch (Exception e) {
            log.error("Failed to send payment approved notification: {}", e.getMessage());
        }

        return approved;
    }

    /**
     * Admin rejects payment submission with mandatory reason.
     */
    public PaymentSubmission rejectPayment(Long paymentId, String reason, Long adminId) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        String cleanReason = reason.trim();
        PaymentSubmission rejected = paymentDAO.rejectPayment(paymentId, cleanReason, adminId);

        // Notify student of rejection with reason
        try {
            String msg = String.format(
                    "Your payment proof for \"%s\" was rejected. Reason: %s. You may resubmit valid proof from the event details page.",
                    rejected.getEventTitle(), cleanReason
            );
            notificationService.sendPaymentNotification(rejected.getStudentId(), rejected.getEventId(),
                    NotificationService.TYPE_PAYMENT_REJECTED, msg);
        } catch (Exception e) {
            log.error("Failed to send payment rejected notification: {}", e.getMessage());
        }

        return rejected;
    }

    /** Find all submissions for admin review with optional status filter. */
    public List<PaymentSubmission> getAllSubmissions(String statusFilter) {
        return paymentDAO.findAll(statusFilter);
    }

    /** Get counts by status. */
    public Map<String, Integer> getCounts() {
        return paymentDAO.countByStatus();
    }

    /** Find single submission by ID. */
    public Optional<PaymentSubmission> getById(Long id) {
        return paymentDAO.findById(id);
    }

    /**
     * Generate registration receipt data.
     * Enforces that receipts are only available for confirmed/approved registrations.
     */
    public Map<String, Object> getReceiptData(Long registrationId, Long requestingUserId, boolean isAdmin) {
        Optional<PaymentSubmission> payOpt = paymentDAO.findByRegistrationId(registrationId);

        if (payOpt.isEmpty()) {
            // Check if it's a free confirmed registration
            String sql = """
                    SELECT r.*, s.name AS student_name, s.register_number, s.department, s.email,
                           e.title AS event_title, e.event_date, e.venue, e.fee
                    FROM registrations r
                    JOIN students s ON r.student_id = s.id
                    JOIN events e ON r.event_id = e.id
                    WHERE r.id = ?
                    """;
            // We can query directly
            throw new IllegalArgumentException("No payment record found for registration ID: " + registrationId);
        }

        PaymentSubmission pay = payOpt.get();

        // Security: only admin or the student who registered can view the receipt
        if (!isAdmin && !pay.getStudentId().equals(requestingUserId)) {
            throw new SecurityException("Access denied: You can only view your own receipts");
        }

        if (!"APPROVED".equalsIgnoreCase(pay.getStatus())) {
            throw new IllegalStateException("Receipt is only available after payment has been verified and approved by admin.");
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("systemTitle", "Campus Event Management System");
        receipt.put("institution", "Kalasalingam Academy of Research and Education");
        receipt.put("receiptNumber", "REC-" + pay.getId() + "-" + pay.getRegistrationId());
        receipt.put("registrationId", "REG-" + pay.getRegistrationId());
        receipt.put("studentName", pay.getStudentName());
        receipt.put("registerNumber", pay.getRegisterNumber());
        receipt.put("email", pay.getEmail());
        receipt.put("department", pay.getDepartment() != null ? pay.getDepartment() : "N/A");
        receipt.put("eventTitle", pay.getEventTitle());
        receipt.put("eventDate", pay.getEventDate() != null ? pay.getEventDate().format(dateFmt) : "TBA");
        receipt.put("venue", pay.getVenue() != null ? pay.getVenue() : "TBA");
        receipt.put("amount", pay.getAmount());
        receipt.put("transactionId", pay.getTransactionId());
        receipt.put("paymentStatus", "APPROVED");
        receipt.put("verifiedBy", pay.getVerifiedByName() != null ? pay.getVerifiedByName() : "Admin Office");
        receipt.put("submittedAt", pay.getSubmittedAt() != null ? pay.getSubmittedAt().format(dateFmt) : "—");
        receipt.put("approvalDate", pay.getVerifiedAt() != null ? pay.getVerifiedAt().format(dateFmt) : "—");

        return receipt;
    }
}
