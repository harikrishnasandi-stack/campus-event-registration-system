package com.campus.events.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Model representing a manual UPI payment submission for paid events.
 * Maps to the 'payment_submissions' table.
 */
public class PaymentSubmission {
    private Long id;
    private Long registrationId;
    private Long studentId;
    private Long eventId;
    private BigDecimal amount;
    private String transactionId;
    private String screenshotPath;
    private String status; // PENDING, APPROVED, REJECTED
    private String rejectionReason;
    private Long verifiedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;

    // Joined fields for admin review & receipt generation
    private String studentName;
    private String registerNumber;
    private String email;
    private String department;
    private String year;
    private String eventTitle;
    private LocalDateTime eventDate;
    private String venue;
    private String verifiedByName;

    public PaymentSubmission() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRegistrationId() { return registrationId; }
    public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getScreenshotPath() { return screenshotPath; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Long getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Long verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public LocalDateTime getEventDate() { return eventDate; }
    public void setEventDate(LocalDateTime eventDate) { this.eventDate = eventDate; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getVerifiedByName() { return verifiedByName; }
    public void setVerifiedByName(String verifiedByName) { this.verifiedByName = verifiedByName; }
}

