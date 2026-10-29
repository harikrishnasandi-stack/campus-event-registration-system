package com.campus.events.util;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Java Collections: Queue implementation for waitlist processing.
 *
 * Uses java.util.Queue (backed by java.util.LinkedList) to maintain FIFO order
 * for waitlisted students when a registered student cancels.
 *
 * Queue operations and their complexity:
 *   offer(x)  — O(1) add to tail
 *   poll()    — O(1) remove from head (FIFO)
 *   peek()    — O(1) view head without removing
 *   isEmpty() — O(1)
 *   size()    — O(1)
 *
 * Called from: RegistrationService.register() when event is full
 */
public class WaitlistQueue {

    private final Queue<Long> queue = new LinkedList<>();

    /** Add a student ID to the end of the waitlist. O(1) */
    public void enqueue(Long studentId) {
        queue.offer(studentId);
    }

    /** Remove and return the first student in the waitlist. O(1) */
    public Long dequeue() {
        return queue.poll();
    }

    /** View the next student without removing. O(1) */
    public Long peek() {
        return queue.peek();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Total number of students in the waitlist. O(1) */
    public int size() {
        return queue.size();
    }

    /**
     * Find the 1-based position of a student in the waitlist.
     * O(n) — acceptable for small waitlists.
     * Returns -1 if student is not in the waitlist.
     */
    public int positionOf(Long studentId) {
        int pos = 1;
        for (Long id : queue) {
            if (id.equals(studentId)) return pos;
            pos++;
        }
        return -1;
    }
}
