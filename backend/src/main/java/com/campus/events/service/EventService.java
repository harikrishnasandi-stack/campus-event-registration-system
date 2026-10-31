package com.campus.events.service;

import com.campus.events.dao.EventDAO;
import com.campus.events.model.Event;
import com.campus.events.util.EventSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Event business logic service.
 *
 * DSA integration: Calls EventSorter.sortByDate() (Merge Sort, O(n log n))
 * on every list returned to clients so events always appear ordered by date.
 *
 * Java Collections: returns ArrayList<Event> from DAO, passes through sorter.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    @Autowired
    private EventDAO eventDAO;

    @Autowired
    private NotificationService notificationService;

    /**
     * All PUBLISHED events, sorted by date ascending.
     * DSA: EventSorter.sortByDate() — Merge Sort O(n log n)
     * Collections: ArrayList<Event>
     */
    public ArrayList<Event> getPublishedEvents() {
        ArrayList<Event> raw = eventDAO.findAllPublished();
        return EventSorter.sortByDate(raw); // Merge Sort applied here
    }

    /**
     * All events for admin view, sorted by date.
     * DSA: Merge Sort applied.
     */
    public ArrayList<Event> getAllEvents() {
        ArrayList<Event> raw = eventDAO.findAll();
        return EventSorter.sortByDate(raw);
    }

    /**
     * Single event by ID — uses HashMap cache in EventDAO (O(1) average).
     */
    public Optional<Event> getEventById(Long id) {
        return eventDAO.findById(id);
    }

    /** Create a new event (admin only). Sets createdBy = adminId and broadcasts announcement notifications. */
    public Event createEvent(Event event, Long adminId) {
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Event title is required");
        }
        if (event.getTotalSeats() <= 0) {
            throw new IllegalArgumentException("Total seats must be greater than zero");
        }
        if (event.getEventDate() != null && event.getRegistrationDeadline() != null) {
            if (event.getRegistrationDeadline().isAfter(event.getEventDate())) {
                throw new IllegalArgumentException("Registration deadline cannot be after the event date");
            }
        }
        if (event.getFee() != null && event.getFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Event fee cannot be negative");
        }
        if (event.getFee() == null) {
            event.setFee(BigDecimal.ZERO);
        }

        // Paid event rule: if published directly, must have UPI QR code
        boolean isPaid = event.getFee().compareTo(BigDecimal.ZERO) > 0;
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus(isPaid && (event.getUpiQrImage() == null || event.getUpiQrImage().isBlank()) ? "DRAFT" : "PUBLISHED");
        } else if ("PUBLISHED".equalsIgnoreCase(event.getStatus()) && isPaid && (event.getUpiQrImage() == null || event.getUpiQrImage().isBlank())) {
            throw new IllegalArgumentException("Paid events (fee > ₹0) require a valid UPI QR code before publishing.");
        }

        event.setCreatedBy(adminId);
        Long id = eventDAO.save(event);
        if (id == null) {
            throw new RuntimeException("Failed to save event to database");
        }
        event.setId(id);

        // Automatically broadcast notification for every registered account if published
        if ("PUBLISHED".equalsIgnoreCase(event.getStatus())) {
            try {
                notificationService.broadcastNewEventAnnouncement(event);
            } catch (Exception e) {
                log.error("Failed to broadcast notifications for new event {}: {}", id, e.getMessage());
            }
        }

        return event;
    }

    /**
     * Update an existing event with validation (admin only).
     * Preserves existing status if not explicitly changed, and validates capacity, dates, and fee.
     */
    public Event updateEvent(Long id, Event incoming) {
        Optional<Event> existingOpt = eventDAO.findById(id);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Event not found with ID: " + id);
        }
        Event existing = existingOpt.get();

        // Validation: required title
        if (incoming.getTitle() == null || incoming.getTitle().trim().isEmpty()) {
            throw new IllegalStateException("Event title is required");
        }

        // Validation: total seats positive
        if (incoming.getTotalSeats() <= 0) {
            throw new IllegalStateException("Total seats must be greater than zero");
        }

        // Validation: total seats cannot be reduced below current registrations
        if (incoming.getTotalSeats() < existing.getRegisteredCount()) {
            throw new IllegalStateException(
                "Total seats cannot be less than currently registered count (" + existing.getRegisteredCount() + ")"
            );
        }

        // Validation: registration deadline cannot be after event date
        if (incoming.getEventDate() != null && incoming.getRegistrationDeadline() != null) {
            if (incoming.getRegistrationDeadline().isAfter(incoming.getEventDate())) {
                throw new IllegalStateException("Registration deadline cannot be after the event date");
            }
        }

        // Fee validation
        if (incoming.getFee() != null) {
            if (incoming.getFee().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Event fee cannot be negative");
            }
            existing.setFee(incoming.getFee());
        }

        // UPI QR image
        if (incoming.getUpiQrImage() != null) {
            existing.setUpiQrImage(incoming.getUpiQrImage());
        }

        // Update fields
        existing.setTitle(incoming.getTitle().trim());
        existing.setDescription(incoming.getDescription());
        existing.setInstructions(incoming.getInstructions());
        existing.setVenue(incoming.getVenue());
        if (incoming.getEventDate() != null) {
            existing.setEventDate(incoming.getEventDate());
        }
        if (incoming.getRegistrationDeadline() != null) {
            existing.setRegistrationDeadline(incoming.getRegistrationDeadline());
        }
        existing.setTotalSeats(incoming.getTotalSeats());

        // Preserve status if not provided in update request (prevent MySQL null status error)
        if (incoming.getStatus() != null && !incoming.getStatus().isBlank()) {
            String newStatus = incoming.getStatus().trim().toUpperCase();
            if ("PUBLISHED".equals(newStatus) && existing.getFee().compareTo(BigDecimal.ZERO) > 0 &&
                    (existing.getUpiQrImage() == null || existing.getUpiQrImage().isBlank())) {
                throw new IllegalStateException("Paid events require a valid UPI QR code before publishing.");
            }
            existing.setStatus(newStatus);
        }

        boolean ok = eventDAO.update(existing);
        if (!ok) {
            throw new RuntimeException("Failed to update event in database");
        }

        return existing;
    }

    /** Change event status to PUBLISHED (DRAFT → PUBLISHED). */
    public Event publishEvent(Long eventId) {
        Optional<Event> opt = eventDAO.findById(eventId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        Event event = opt.get();
        if ("PUBLISHED".equalsIgnoreCase(event.getStatus())) {
            throw new IllegalStateException("Event is already published");
        }
        if (event.getFee().compareTo(BigDecimal.ZERO) > 0 &&
                (event.getUpiQrImage() == null || event.getUpiQrImage().isBlank())) {
            throw new IllegalStateException("Paid events (fee > ₹0) require a valid UPI QR code before publishing.");
        }

        boolean ok = eventDAO.updateStatus(eventId, "PUBLISHED");
        if (!ok) {
            throw new RuntimeException("Failed to update event status to PUBLISHED");
        }
        event.setStatus("PUBLISHED");

        try {
            notificationService.broadcastNewEventAnnouncement(event);
        } catch (Exception e) {
            log.error("Failed to broadcast notifications on publish for event {}: {}", eventId, e.getMessage());
        }

        return event;
    }

    /** Cancel an event (any status → CANCELLED). */
    public Event cancelEvent(Long eventId) {
        Optional<Event> opt = eventDAO.findById(eventId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        Event event = opt.get();
        if ("CANCELLED".equalsIgnoreCase(event.getStatus())) {
            throw new IllegalStateException("Event is already cancelled");
        }
        boolean ok = eventDAO.updateStatus(eventId, "CANCELLED");
        if (!ok) {
            throw new RuntimeException("Failed to update event status to CANCELLED");
        }
        event.setStatus("CANCELLED");
        return event;
    }

    /** Mark an event as completed (PUBLISHED → COMPLETED). */
    public Event completeEvent(Long eventId) {
        Optional<Event> opt = eventDAO.findById(eventId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        Event event = opt.get();
        if ("COMPLETED".equalsIgnoreCase(event.getStatus())) {
            throw new IllegalStateException("Event is already marked as completed");
        }
        boolean ok = eventDAO.updateStatus(eventId, "COMPLETED");
        if (!ok) {
            throw new RuntimeException("Failed to update event status to COMPLETED");
        }
        event.setStatus("COMPLETED");
        return event;
    }

    /**
     * Delete an event with database safety:
     * - Published events with active registrations must be cancelled first.
     * - Events with registrations, payments, or certificates are safely archived (soft deleted)
     *   to preserve historical records and financial/audit logs.
     * - Events with no dependent records are permanently deleted.
     */
    public boolean deleteEvent(Long eventId) {
        Optional<Event> opt = eventDAO.findById(eventId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        Event event = opt.get();

        // Safety: Prevent accidental deletion of published events that have active registrations. Require cancellation first.
        if ("PUBLISHED".equalsIgnoreCase(event.getStatus()) && event.getRegisteredCount() > 0) {
            throw new IllegalStateException("Cannot delete an active published event with registrations. Please cancel the event first.");
        }

        boolean hasDependencies = eventDAO.hasDependentRecords(eventId);
        if (hasDependencies) {
            log.info("Event {} has registrations/payments/certificates — performing safe soft-delete/archiving", eventId);
            return eventDAO.softDelete(eventId);
        } else {
            log.info("Event {} has no dependent records — performing permanent deletion", eventId);
            return eventDAO.hardDelete(eventId);
        }
    }

    /** Update QR code image path */
    public boolean updateQrImage(Long eventId, String qrImagePath) {
        return eventDAO.updateQrImage(eventId, qrImagePath);
    }
}

