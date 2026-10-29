package com.campus.events.controller;

import com.campus.events.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Certificate REST controller.
 *
 * POST /api/certificates/upload              → upload PDF (admin only)
 * GET  /api/certificates/download?studentId=X&eventId=Y → download PDF
 * GET  /api/certificates/my                  → student's own certs
 * GET  /api/certificates/event/{eventId}     → certs for event (admin)
 * GET  /api/certificates/all                 → all certs (admin)
 *
 * Security: download endpoint checks ownership in CertificateService —
 * students can ONLY download their OWN certificate.
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    @Autowired
    private CertificateService certificateService;

    /**
     * Upload a PDF certificate for a specific student + event.
     * Admin only — @PreAuthorize enforces this at Spring Security level.
     *
     * @param file      multipart PDF file (max 10 MB)
     * @param studentId target student
     * @param eventId   target event
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> upload(
            @RequestParam("file")      MultipartFile file,
            @RequestParam("studentId") Long studentId,
            @RequestParam("eventId")   Long eventId,
            Authentication auth) {
        try {
            Long adminId = (Long) auth.getPrincipal();
            var cert = certificateService.uploadCertificate(file, studentId, eventId, adminId);
            return ResponseEntity.ok(Map.of(
                    "message",  "Certificate uploaded successfully",
                    "fileName", cert.getFileName(),
                    "fileSize", cert.getFileSize()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * Download a certificate PDF.
     * Ownership enforced in CertificateService:
     *  - STUDENT → can only download their own cert
     *  - ADMIN   → can download any cert (for verification)
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @RequestParam("studentId") Long studentId,
            @RequestParam("eventId")   Long eventId,
            Authentication auth) {
        try {
            Long   requesterId   = (Long) auth.getPrincipal();
            String requesterRole = auth.getAuthorities().iterator().next()
                                       .getAuthority().replace("ROLE_", "");

            Path     filePath = certificateService.getCertificateFile(studentId, eventId, requesterId, requesterRole);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) return ResponseEntity.notFound().build();

            String filename = "certificate_" + studentId + "_event_" + eventId + ".pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Student views their own certificates. */
    @GetMapping("/my")
    public ResponseEntity<?> getMyCertificates(Authentication auth) {
        Long studentId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(certificateService.getStudentCertificates(studentId));
    }

    /** All certs for an event (admin). */
    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getEventCertificates(@PathVariable Long eventId) {
        return ResponseEntity.ok(certificateService.getEventCertificates(eventId));
    }

    /** All certs in the system (admin). */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllCertificates() {
        return ResponseEntity.ok(certificateService.getAllCertificates());
    }
}

