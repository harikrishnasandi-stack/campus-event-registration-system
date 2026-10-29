package com.campus.events.service;

import com.campus.events.dao.CertificateDAO;
import com.campus.events.model.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Certificate upload and download service.
 *
 * Security measures implemented:
 *  1. PDF-only validation — checks both content-type AND .pdf extension
 *  2. File size limit — max 10 MB
 *  3. Path traversal prevention — Paths.get().normalize() + startsWith() guard
 *  4. UUID random stored filename — prevents filename enumeration/guessing
 *  5. Files stored outside webroot — not directly browser-accessible
 *  6. Ownership check — students can only download their OWN certificates
 *  7. Admin restriction — only admins can upload (enforced at controller via @PreAuthorize)
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private static final long   MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    @Autowired
    private CertificateDAO certificateDAO;

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * Upload a PDF certificate for a student+event.
     * Only admins call this (enforced by @PreAuthorize in controller).
     *
     * @param file      the uploaded MultipartFile
     * @param studentId target student's ID
     * @param eventId   target event's ID
     * @param adminId   the admin performing the upload (for audit)
     * @return the saved Certificate metadata
     */
    public Certificate uploadCertificate(MultipartFile file,
                                          Long studentId,
                                          Long eventId,
                                          Long adminId) throws IOException {
        // ── Validation ─────────────────────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds 10 MB limit");
        }

        // Content-type check (sent by browser/client)
        String ct = file.getContentType();
        if (!"application/pdf".equalsIgnoreCase(ct)) {
            throw new IllegalArgumentException("Only PDF files are allowed (got: " + ct + ")");
        }

        // Extension check — defense in depth against spoofed content-type
        String origName = file.getOriginalFilename();
        if (origName == null || !origName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must have a .pdf extension");
        }

        // ── Filename sanitisation ──────────────────────────────────────────────
        // Strip any path components, replace dangerous characters
        String safeName = Paths.get(origName).getFileName().toString()
                               .replaceAll("[^a-zA-Z0-9._\\-]", "_");

        // UUID prefix makes stored filename unpredictable (prevents enumeration)
        String storedName = UUID.randomUUID() + "_" + safeName;

        // ── Path traversal guard ───────────────────────────────────────────────
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        Path target = uploadPath.resolve(storedName).normalize();

        // Final check: target must still be inside uploadPath
        if (!target.startsWith(uploadPath)) {
            throw new SecurityException("Path traversal detected — upload rejected");
        }

        // ── Write file ────────────────────────────────────────────────────────
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Certificate saved → {} (student={}, event={})", storedName, studentId, eventId);

        // ── Persist metadata ──────────────────────────────────────────────────
        Certificate cert = new Certificate();
        cert.setStudentId(studentId);
        cert.setEventId(eventId);
        cert.setFileName(safeName);
        cert.setStoredFileName(storedName);
        cert.setFileSize(file.getSize());
        cert.setUploadedBy(adminId);

        certificateDAO.save(cert);
        return cert;
    }

    /**
     * Resolve the Path of a certificate file, with ownership enforcement.
     *
     * @param studentId     the student the cert belongs to
     * @param eventId       the event
     * @param requesterId   who is making the request (from JWT)
     * @param requesterRole STUDENT or ADMIN (from JWT)
     */
    public Path getCertificateFile(Long studentId, Long eventId,
                                    Long requesterId, String requesterRole) throws IOException {
        // Ownership check: students may only get their OWN certificate
        if ("STUDENT".equals(requesterRole) && !requesterId.equals(studentId)) {
            throw new SecurityException("Access denied: you may only download your own certificates");
        }

        Optional<Certificate> opt = certificateDAO.findByStudentAndEvent(studentId, eventId);
        if (opt.isEmpty()) {
            throw new IOException("Certificate not found");
        }

        Certificate cert = opt.get();
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath   = uploadPath.resolve(cert.getStoredFileName()).normalize();

        // Path traversal guard on retrieval too
        if (!filePath.startsWith(uploadPath)) {
            throw new SecurityException("Invalid certificate path");
        }
        if (!Files.exists(filePath)) {
            throw new IOException("Certificate file not found on server");
        }

        return filePath;
    }

    public ArrayList<Certificate> getStudentCertificates(Long studentId) {
        return certificateDAO.findByStudentId(studentId);
    }

    public ArrayList<Certificate> getEventCertificates(Long eventId) {
        return certificateDAO.findByEventId(eventId);
    }

    public ArrayList<Certificate> getAllCertificates() {
        return certificateDAO.findAll();
    }
}

