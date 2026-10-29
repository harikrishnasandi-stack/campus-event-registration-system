package com.campus.events.service;

import com.campus.events.dao.RegistrationDAO;
import com.campus.events.model.Registration;
import com.campus.events.util.WaitlistQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Registration business logic service.
 *
 * Java Collections used:
 *   - HashMap<Long, WaitlistQueue>: per-event in-memory waitlist queues
 *   - WaitlistQueue wraps java.util.Queue<Long> (FIFO LinkedList)
 *   - ArrayList<Registration> from DAO
 *
 * Transaction safety: DB-level transaction + UNIQUE constraint in RegistrationDAO
 * are the authoritative guards. The HashSet pre-check in RegistrationDAO is a fast
 * first filter; this service adds Queue management on top.
 */
@Service
public class RegistrationService {

    @Autowired
    private RegistrationDAO registrationDAO;

    /**
     * Java Collections: HashMap maps eventId → its WaitlistQueue.
     * Each WaitlistQueue wraps a java.util.Queue<Long> (FIFO).
     */
    private final HashMap<Long, WaitlistQueue> waitlists = new HashMap<>();

    /**
     * Register a student for an event.
     *
     * Flow:
     *  1. HashSet pre-check in RegistrationDAO (O(1))
     *  2. DB transaction: lock row, check seats, insert, increment count
     *  3. If WAITLISTED, enqueue student ID in the event's Queue
     *
     * @return Map with "status" (REGISTERED|WAITLISTED) and "message"
     */
    public Map<String, Object> register(Long studentId, Long eventId) {
        // Fast pre-check using HashSet (O(1)) before hitting DB
        if (registrationDAO.isAlreadyRegistered(studentId, eventId)) {
            throw new RuntimeException("You are already registered for this event");
        }

        // Authoritative check: DB transaction with row-level lock
        String status = registrationDAO.registerStudent(studentId, eventId);

        // If waitlisted, add to the in-memory FIFO Queue for this event
        if ("WAITLISTED".equals(status)) {
            WaitlistQueue queue = waitlists.computeIfAbsent(eventId, k -> new WaitlistQueue());
            queue.enqueue(studentId); // O(1) — adds to tail of LinkedList
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("message", "REGISTERED".equals(status)
                ? "Successfully registered for the event!"
                : "Event is full. You have been added to the waitlist (position " + getWaitlistPosition(eventId, studentId) + ").");
        return result;
    }

    /** Get all registrations for a student (own records only). */
    public ArrayList<Registration> getStudentRegistrations(Long studentId) {
        return registrationDAO.findByStudentId(studentId);
    }

    /** Get all registrations for an event (admin only). */
    public ArrayList<Registration> getEventRegistrations(Long eventId) {
        return registrationDAO.findByEventId(eventId);
    }

    /**
     * Get current waitlist position for a student in an event.
     * Iterates the Queue to find position (O(n) — acceptable for small waitlists).
     */
    public int getWaitlistPosition(Long eventId, Long studentId) {
        WaitlistQueue queue = waitlists.get(eventId);
        if (queue == null) return -1;
        return queue.positionOf(studentId);
    }

    /** Get total waitlist size for an event. Uses Queue.size() O(1). */
    public int getWaitlistSize(Long eventId) {
        WaitlistQueue queue = waitlists.get(eventId);
        return queue != null ? queue.size() : 0;
    }
}

