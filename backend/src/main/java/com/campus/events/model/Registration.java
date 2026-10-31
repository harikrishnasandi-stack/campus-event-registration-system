package com.campus.events.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Model class representing a student's registration for an event.
 * Maps to the 'registrations' table.
 * Status: REGISTERED, WAITLISTED, CANCELLED, PENDING
 * PaymentStatus: NOT_REQUIRED, PENDING, APPROVED, REJECTED
 */
public class Registration {
    private Long id;
    private Long studentId;
    private Long eventId;
    private String status; // REGISTERED, WAITLISTED, CANCELLED, PENDING
    private String paymentStatus = "NOT_REQUIRED"; // NOT_REQUIRED, PENDING, APPROVED, REJECTED
    private LocalDateTime registeredAt;

    // Joined fields for display
    private String studentName;
    private String registerNumber;
    private String email;
    private String department;
    private String year;
    private String eventTitle;
    private LocalDateTime eventDate;
    private String eventStatus;
    private String venue;
    private BigDecimal amount;
    private String transactionId;
    private String rejectionReason;

    public Registration() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus != null ? paymentStatus : "NOT_REQUIRED"; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

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

    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
