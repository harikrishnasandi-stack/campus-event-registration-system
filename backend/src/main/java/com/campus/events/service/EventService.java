package com.campus.events.service;

import com.campus.events.dao.EventDAO;
import com.campus.events.model.Event;
import com.campus.events.util.EventSorter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired
    private EventDAO eventDAO;

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

    /** Create a new event (admin only). Sets createdBy = adminId. */
    public Event createEvent(Event event, Long adminId) {
        event.setCreatedBy(adminId);
        if (event.getStatus() == null) event.setStatus("DRAFT");
        Long id = eventDAO.save(event);
        event.setId(id);
        return event;
    }

    /**
     * Update an existing event with validation (admin only).
     * Preserves existing status if not explicitly changed, and validates capacity and dates.
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
            existing.setStatus(incoming.getStatus().trim().toUpperCase());
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
        boolean ok = eventDAO.updateStatus(eventId, "PUBLISHED");
        if (!ok) {
            throw new RuntimeException("Failed to update event status to PUBLISHED");
        }
        event.setStatus("PUBLISHED");
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
}

