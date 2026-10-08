import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Payanam Junction - Railway Reservation Prototype
 *
 * TrainService represents a single train in the reservation system.
 *
 * Architectural & Data Structure Justifications:
 * - WHY sorted ArrayList is used for confirmedReservations:
 *   Represents chronological reservation history and meets project data-structure
 *   requirements. Maintained in strictly sorted order by bookingTime at all times using
 *   Collections.binarySearch(), which leverages the O(1) random-access capabilities of
 *   ArrayList for efficient logarithmic-time position lookup.
 *
 * - WHY ArrayDeque is used for waitingQueue:
 *   Provides amortized O(1) FIFO enqueue (addLast) and dequeue (pollFirst) operations
 *   without the memory and node allocation overhead of LinkedList, ensuring strict
 *   fairness and true First-Come, First-Served (FCFS) waiting-list processing.
 *
 * NOTE: Schedule-related fields (arrivalTime, departureTime, trainLength, declaredPriority)
 * are included strictly for future scheduling phases and MUST NOT be used in seat allocation
 * or booking logic.
 */
public class TrainService {

    /** Default fixed passenger seating capacity. */
    public static final int DEFAULT_CAPACITY = 10;

    /** Operational priorities for future scheduling phase. */
    public static final String PRIORITY_EMERGENCY = "EMERGENCY";
    public static final String PRIORITY_CONNECTING = "CONNECTING";
    public static final String PRIORITY_SCHEDULED = "SCHEDULED";

    /** Comparator to maintain confirmedReservations sorted chronologically by bookingTime. */
    private static final Comparator<Reservation> BOOKING_TIME_COMPARATOR =
            Comparator.comparingLong(Reservation::getBookingTime);

    /** Unique identifier for the train (e.g., "TR101", "EXP202"). */
    private final String trainId;

    /** Name of the train service (e.g., "Vaigai Express", "Cheran Express"). */
    private final String name;

    /** Maximum seating capacity of the train, fixed at 10. */
    private final int capacity;

    /**
     * Chronological list of confirmed reservations.
     * Backed by an ArrayList to preserve the chronological reservation sequence
     * and allow efficient sequential traversal.
     */
    private final List<Reservation> confirmedReservations;

    /**
     * Waiting list queue for passengers when the train reaches full capacity.
     * Backed by an ArrayDeque for O(1) FIFO operations ensuring fair
     * First-Come, First-Served (FCFS) ticket allocation when seats become available.
     */
    private final ArrayDeque<Passenger> waitingQueue;

    // =========================================================================
    // Schedule-related fields (for future phase only; NOT used in booking logic)
    // =========================================================================

    /** Scheduled arrival time (e.g., "08:30" or "08:30 AM"). */
    private String arrivalTime;

    /** Scheduled departure time (e.g., "08:45" or "08:45 AM"). */
    private String departureTime;

    /** Length of the train (e.g., number of coaches or total length). */
    private int trainLength;

    /** Declared operational priority: "EMERGENCY", "CONNECTING", or "SCHEDULED". */
    private String declaredPriority;

    /**
     * Constructs a new TrainService with fixed capacity of 10.
     *
     * @param trainId unique identifier for the train; must not be null
     * @param name    name of the train service; must not be null
     * @throws NullPointerException if trainId or name is null
     */
    public TrainService(String trainId, String name) {
        this.trainId = Objects.requireNonNull(trainId, "trainId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.capacity = DEFAULT_CAPACITY;
        this.confirmedReservations = new ArrayList<>();
        this.waitingQueue = new ArrayDeque<>();
        this.declaredPriority = PRIORITY_SCHEDULED;
    }

    /**
     * Sets the schedule and operational attributes for this train.
     * NOTE: These fields are reserved for future scheduling phases only and
     * MUST NOT be used in seat allocation or booking logic.
     *
     * @param arrivalTime      scheduled arrival time
     * @param departureTime    scheduled departure time
     * @param trainLength      length of the train
     * @param declaredPriority operational priority ("EMERGENCY", "CONNECTING", or "SCHEDULED")
     */
    public void setSchedule(String arrivalTime, String departureTime, int trainLength, String declaredPriority) {
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.trainLength = trainLength;
        this.declaredPriority = declaredPriority;
    }

    /**
     * Returns the unique identifier of the train.
     *
     * @return the trainId
     */
    public String getTrainId() {
        return trainId;
    }

    /**
     * Returns the name of the train.
     *
     * @return the train name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the maximum seating capacity of the train.
     *
     * @return the capacity (fixed at 10)
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Returns an unmodifiable view of the confirmed reservations.
     * Preserves chronological sorting by bookingTime and prevents external modification.
     *
     * @return unmodifiable List view of confirmed reservations
     */
    public List<Reservation> getConfirmedReservations() {
        return Collections.unmodifiableList(confirmedReservations);
    }

    /**
     * Returns a snapshot copy of the waiting queue.
     * Backed by a new List copy to preserve FIFO order while ensuring the internal
     * live ArrayDeque is never directly exposed.
     *
     * @return a new List copy containing the waiting passengers in FIFO order
     */
    public List<Passenger> getWaitingListSnapshot() {
        return new ArrayList<>(waitingQueue);
    }

    /**
     * Returns the number of passengers currently on the waiting list.
     *
     * @return waiting list passenger count
     */
    public int getWaitingCount() {
        return waitingQueue.size();
    }

    /**
     * Returns the scheduled arrival time (future phase).
     *
     * @return arrival time
     */
    public String getArrivalTime() {
        return arrivalTime;
    }

    /**
     * Returns the scheduled departure time (future phase).
     *
     * @return departure time
     */
    public String getDepartureTime() {
        return departureTime;
    }

    /**
     * Returns the train length (future phase).
     *
     * @return train length
     */
    public int getTrainLength() {
        return trainLength;
    }

    /**
     * Returns the declared operational priority (future phase).
     *
     * @return declared priority
     */
    public String getDeclaredPriority() {
        return declaredPriority;
    }

    /**
     * Attempts to book a seat for the specified passenger.
     * <p>
     * Logic:
     * <ul>
     *   <li>If {@code confirmedReservations.size() < capacity}:
     *       generates a reservation ID, creates a {@link Reservation},
     *       finds the insertion position using {@code Collections.binarySearch()}
     *       with a comparator based on {@code Reservation.bookingTime}, inserts
     *       the reservation at that position, and returns it.</li>
     *   <li>Otherwise:
     *       adds the passenger to the END of {@code waitingQueue} using {@code addLast()},
     *       and returns {@code null} indicating the passenger is waitlisted.</li>
     * </ul>
     *
     * @param passenger   the passenger requesting a ticket; must not be null
     * @param idGenerator the generator used to produce unique reservation IDs; must not be null
     * @return the confirmed {@link Reservation}, or {@code null} if the passenger was placed on the waiting list
     * @throws NullPointerException if passenger or idGenerator is null
     */
    public Reservation bookSeat(Passenger passenger, ReservationIdGenerator idGenerator) {
        Objects.requireNonNull(passenger, "passenger must not be null");
        Objects.requireNonNull(idGenerator, "idGenerator must not be null");

        if (confirmedReservations.size() < capacity) {
            String reservationId = idGenerator.next();
            int seatNumber = confirmedReservations.size() + 1;
            Reservation reservation = new Reservation(reservationId, passenger, this.trainId, seatNumber);

            // Insert into confirmedReservations in bookingTime order
            addConfirmedReservationSorted(reservation);

            return reservation;
        } else {
            waitingQueue.addLast(passenger);
            return null;
        }
    }

    /**
     * Cancels an existing confirmed reservation on this train and reallocates
     * the freed seat to the next waiting passenger in strict FIFO order, if any.
     * <p>
     * Logic:
     * <ol>
     *   <li>Find the confirmed reservation with this ID on this train.</li>
     *   <li>If it does not exist, throw {@link IllegalArgumentException}.</li>
     *   <li>If it is already cancelled, throw {@link IllegalArgumentException}.</li>
     *   <li>Mark it CANCELLED.</li>
     *   <li>Remove it from confirmedReservations.</li>
     *   <li>Get the FIRST passenger from waitingQueue using {@code pollFirst()}.</li>
     *   <li>If a waiting passenger exists:
     *     <ul>
     *       <li>Create a new Reservation for that passenger.</li>
     *       <li>Use the seat number that was just freed.</li>
     *       <li>Add the new reservation to confirmedReservations in bookingTime order.</li>
     *       <li>Return the new Reservation.</li>
     *     </ul>
     *   </li>
     *   <li>If nobody is waiting, return {@code null}.</li>
     * </ol>
     *
     * @param reservationId the ID of the reservation to cancel; must not be null
     * @param idGenerator   the generator used to produce reservation IDs; must not be null
     * @return the new confirmed {@link Reservation} for the promoted waiting passenger, or {@code null} if no passenger was waiting
     * @throws IllegalArgumentException if the reservation does not exist or is already cancelled
     * @throws NullPointerException     if reservationId or idGenerator is null
     */
    public Reservation cancelBooking(String reservationId, ReservationIdGenerator idGenerator) {
        if (reservationId == null) {
            throw new IllegalArgumentException("Reservation ID cannot be null");
        }
        Objects.requireNonNull(idGenerator, "idGenerator must not be null");

        // 1. Find the confirmed reservation with this ID on this train
        Reservation foundReservation = null;
        for (Reservation res : confirmedReservations) {
            if (res.getReservationId().equals(reservationId)) {
                foundReservation = res;
                break;
            }
        }

        // 2. If it does not exist, throw IllegalArgumentException
        if (foundReservation == null) {
            throw new IllegalArgumentException("Reservation not found with ID: " + reservationId);
        }

        // 3. If it is already cancelled, throw IllegalArgumentException
        if (foundReservation.getStatus() == Reservation.Status.CANCELLED) {
            throw new IllegalArgumentException("Reservation is already cancelled: " + reservationId);
        }

        // 4. Mark it CANCELLED
        foundReservation.markCancelled();

        // 5. Remove it from confirmedReservations and capture freed seat number
        int freedSeatNumber = foundReservation.getSeatNumber();
        confirmedReservations.remove(foundReservation);

        // 6. Get the FIRST passenger from waitingQueue using pollFirst()
        Passenger nextPassenger = waitingQueue.pollFirst();

        // 7. If a waiting passenger exists, allocate the freed seat and insert in bookingTime order
        if (nextPassenger != null) {
            String newReservationId = idGenerator.next();
            Reservation newReservation = new Reservation(newReservationId, nextPassenger, this.trainId, freedSeatNumber);
            addConfirmedReservationSorted(newReservation);
            return newReservation;
        }

        // 8. If nobody is waiting, return null
        return null;
    }

    /**
     * Inserts a reservation into confirmedReservations maintaining chronological
     * sorting by bookingTime using Collections.binarySearch.
     *
     * @param reservation the reservation to insert
     */
    private void addConfirmedReservationSorted(Reservation reservation) {
        int index = Collections.binarySearch(confirmedReservations, reservation, BOOKING_TIME_COMPARATOR);
        if (index < 0) {
            index = -(index + 1);
        }
        confirmedReservations.add(index, reservation);
    }

    /**
     * Checks if the train has reached its maximum seating capacity.
     *
     * @return true if confirmed reservations have reached or exceeded capacity, false otherwise
     */
    public boolean isFull() {
        return confirmedReservations.size() >= capacity;
    }

    /**
     * Returns the number of currently available seats.
     *
     * @return available seat count
     */
    public int getAvailableSeats() {
        return Math.max(0, capacity - confirmedReservations.size());
    }

    /**
     * Returns a readable, concise summary of the train service.
     * Summarizes the train name, train ID, available seats vs total capacity,
     * waiting passenger count, and schedule information.
     *
     * @return readable summary string
     */
    @Override
    public String toString() {
        String scheduleDetails = String.format("Arr: %s, Dep: %s, Length: %d, Priority: %s",
                arrivalTime != null ? arrivalTime : "N/A",
                departureTime != null ? departureTime : "N/A",
                trainLength,
                declaredPriority);

        return String.format("Train: %s (ID: %s) | Available Seats: %d/%d | Waiting: %d | Schedule: [%s]",
                name, trainId, getAvailableSeats(), capacity, getWaitingCount(), scheduleDetails);
    }
}
