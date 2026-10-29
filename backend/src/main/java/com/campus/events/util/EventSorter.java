package com.campus.events.util;

import com.campus.events.model.Event;
import java.util.ArrayList;
import java.util.List;

/**
 * DSA Implementation: Merge Sort for Events by Date.
 *
 * Time Complexity:  O(n log n) — guaranteed for all cases
 * Space Complexity: O(n) — auxiliary arrays during merge
 *
 * Why Merge Sort?
 * - Stable sort: events on the same date retain insertion order
 * - Consistent O(n log n) unlike QuickSort's O(n²) worst case
 * - Easy to explain during a viva
 *
 * Called from: EventService.getAllPublishedEvents() and EventService.getUpcomingEvents()
 */
public class EventSorter {

    /**
     * Public entry point: sorts a list of events by eventDate ascending.
     * @param events List of Event objects (ArrayList)
     * @return New sorted ArrayList (original list is not mutated)
     */
    public static ArrayList<Event> sortByDate(List<Event> events) {
        ArrayList<Event> arr = new ArrayList<>(events);
        if (arr.size() <= 1) return arr;
        mergeSort(arr, 0, arr.size() - 1);
        return arr;
    }

    /**
     * Recursive merge sort - divides array into halves.
     * Complexity: T(n) = 2T(n/2) + O(n)  => O(n log n) by Master Theorem
     */
    private static void mergeSort(ArrayList<Event> arr, int left, int right) {
        if (left >= right) return; // base case: single element

        int mid = left + (right - left) / 2; // avoid integer overflow
        mergeSort(arr, left, mid);            // sort left half
        mergeSort(arr, mid + 1, right);       // sort right half
        merge(arr, left, mid, right);         // merge sorted halves
    }

    /**
     * Merge step: combines two sorted subarrays into one sorted subarray.
     * O(n) time per call, called O(log n) levels => total O(n log n)
     */
    private static void merge(ArrayList<Event> arr, int left, int mid, int right) {
        // Copy elements into temporary lists
        List<Event> leftPart = new ArrayList<>(arr.subList(left, mid + 1));
        List<Event> rightPart = new ArrayList<>(arr.subList(mid + 1, right + 1));

        int i = 0, j = 0, k = left;

        // Compare and merge back into original array
        while (i < leftPart.size() && j < rightPart.size()) {
            // Compare by eventDate; nulls go to the end
            boolean leftFirst = compareEvents(leftPart.get(i), rightPart.get(j)) <= 0;
            if (leftFirst) {
                arr.set(k++, leftPart.get(i++));
            } else {
                arr.set(k++, rightPart.get(j++));
            }
        }

        // Copy remaining elements
        while (i < leftPart.size()) arr.set(k++, leftPart.get(i++));
        while (j < rightPart.size()) arr.set(k++, rightPart.get(j++));
    }

    /**
     * Comparator for events: sorts by eventDate ascending.
     * Null dates are placed at the end.
     */
    private static int compareEvents(Event a, Event b) {
        if (a.getEventDate() == null && b.getEventDate() == null) return 0;
        if (a.getEventDate() == null) return 1;
        if (b.getEventDate() == null) return -1;
        return a.getEventDate().compareTo(b.getEventDate());
    }
}
