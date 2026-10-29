package com.campus.events.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * Notification domain model.
 * Represents an in-app notification for a student (e.g. registration deadline reminder).
 */
public class Notification {
    private Long id;
    private Long studentId;
    private Long eventId;
    private String notificationType;
    private String message;

    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime createdAt;

    // Enriched fields for client rendering (joined from events table)
    private String eventTitle;
    private LocalDateTime registrationDeadline;

    public Notification() {}

    public Notification(Long studentId, Long eventId, String notificationType, String message) {
        this.studentId = studentId;
        this.eventId = eventId;
        this.notificationType = notificationType;
        this.message = message;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }

    @JsonProperty("isRead")
    public void setRead(boolean read) {
        isRead = read;
    }

    @JsonProperty("read")
    public boolean getRead() {
        return isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public LocalDateTime getRegistrationDeadline() {
        return registrationDeadline;
    }

    public void setRegistrationDeadline(LocalDateTime registrationDeadline) {
        this.registrationDeadline = registrationDeadline;
    }
}

