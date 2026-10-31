package com.campus.events.controller;

import com.campus.events.model.PaymentSubmission;
import com.campus.events.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for manual UPI payment verification workflow.
 *
 * POST /api/payments/submit           -> Student submits transaction ID + screenshot
 * GET  /api/payments/admin/all        -> Admin lists all submissions with optional filter
 * GET  /api/payments/admin/counts     -> Admin dashboard filter badge counts
 * POST /api/payments/{id}/approve     -> Admin approves payment & confirms registration
 * POST /api/payments/{id}/reject      -> Admin rejects payment with reason
 * GET  /api/payments/{id}/proof       -> Secure image access (Admin or owning Student only)
 * GET  /api/payments/receipt/{regId}  -> Verified registration receipt details
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /**
     * Submit payment proof (Student only).
     * studentId is strictly extracted from JWT token.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitProof(
            @RequestParam("eventId") Long eventId,
            @RequestParam("transactionId") String transactionId,
            @RequestParam("screenshot") MultipartFile screenshot,
            Authentication auth) {
        try {
            Long studentId = (Long) auth.getPrincipal();
            PaymentSubmission sub = paymentService.submitPaymentProof(studentId, eventId, transactionId, screenshot);
            return ResponseEntity.ok(Map.of(
                    "message", "Payment proof submitted successfully. Your registration is awaiting admin verification.",
                    "submission", sub
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to submit payment proof: " + e.getMessage()));
        }
    }

    /**
     * Get all payment submissions (Admin only).
     * Filterable by status (ALL, PENDING, APPROVED, REJECTED).
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllSubmissions(@RequestParam(value = "status", required = false, defaultValue = "ALL") String status) {
        return ResponseEntity.ok(paymentService.getAllSubmissions(status));
    }

    /**
     * Get counts for dashboard badge filters (Admin only).
     */
    @GetMapping("/admin/counts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCounts() {
        return ResponseEntity.ok(paymentService.getCounts());
    }

    /**
     * Approve payment submission (Admin only).
     * Atomically marks payment APPROVED and registration CONFIRMED.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approvePayment(@PathVariable Long id, Authentication auth) {
        try {
            Long adminId = (Long) auth.getPrincipal();
            PaymentSubmission approved = paymentService.approvePayment(id, adminId);
            return ResponseEntity.ok(Map.of(
                    "message", "Payment approved and registration confirmed successfully",
                    "submission", approved
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject payment submission with mandatory reason (Admin only).
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectPayment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        try {
            Long adminId = (Long) auth.getPrincipal();
            String reason = body != null ? body.get("reason") : null;
            if (reason == null || reason.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
            }
            PaymentSubmission rejected = paymentService.rejectPayment(id, reason, adminId);
            return ResponseEntity.ok(Map.of(
                    "message", "Payment rejected successfully",
                    "submission", rejected
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * View payment proof image.
     * Controlled access:
     * - Admins can view any payment proof.
     * - Students can only view their OWN payment proof.
     */
    @GetMapping("/{id}/proof")
    public ResponseEntity<?> getPaymentProof(@PathVariable Long id, Authentication auth) {
        try {
            Optional<PaymentSubmission> opt = paymentService.getById(id);
            if (opt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            PaymentSubmission sub = opt.get();

            Long requestingUserId = (Long) auth.getPrincipal();
            boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

            if (!isAdmin && !sub.getStudentId().equals(requestingUserId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied: You cannot view another student's payment proof."));
            }

            Path p1 = Paths.get("./uploads/payments").toAbsolutePath().normalize().resolve(sub.getScreenshotPath()).normalize();
            Path p2 = Paths.get("./backend/uploads/payments").toAbsolutePath().normalize().resolve(sub.getScreenshotPath()).normalize();
            Path target = (Files.exists(p1) && p1.startsWith(Paths.get("./uploads/payments").toAbsolutePath().normalize())) ? p1 : p2;

            if (!Files.exists(target)) {
                return ResponseEntity.notFound().build();
            }

            String fileName = sub.getScreenshotPath().toLowerCase();
            String contentType = fileName.endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
            byte[] bytes = Files.readAllBytes(target);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=1800")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download or view registration receipt for confirmed registration.
     */
    @GetMapping("/receipt/{registrationId}")
    public ResponseEntity<?> getReceipt(@PathVariable Long registrationId, Authentication auth) {
        try {
            Long requestingUserId = (Long) auth.getPrincipal();
            boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            Map<String, Object> receipt = paymentService.getReceiptData(registrationId, requestingUserId, isAdmin);
            return ResponseEntity.ok(receipt);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}

