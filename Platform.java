import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Platform represents a physical railway platform at Payanam Junction.
 * Tracks platform dimensions (length in meters) and currently allocated time windows.
 *
 * Architectural & Data Structure Justifications:
 * - WHY sorted ArrayList is used for occupiedIntervals:
 *   Consistent with the architecture used in {@link TrainService#getConfirmedReservations()},
 *   the occupied intervals are stored in a contiguous, randomly-accessible ArrayList and
 *   kept strictly sorted by startMinutes using {@link Collections#binarySearch(List, Object, Comparator)}.
 *   This provides fast logarithmic O(log N) search for insertion points and sequential
 *   cache-friendly traversal during compatibility checking.
 */
public class Platform {

    /** Comparator to maintain occupiedIntervals sorted chronologically by startMinutes. */
    private static final Comparator<Interval> INTERVAL_START_COMPARATOR =
            Comparator.comparingInt(Interval::getStartMinutes);

    /** Unique platform identifier (e.g., "PF1", "PF2"). */
    private final String id;

    /** Physical usable track length of the platform in meters. */
    private final int length;

    /**
     * Chronologically sorted list of allocated time intervals.
     * Maintained sorted by startMinutes at all times.
     */
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Platform represents a physical railway station platform track with a fixed
 * length limit and a sorted schedule of occupied time intervals.
 *
 * Data Structure Justification:
 * occupiedIntervals is stored as an ArrayList maintained in strictly sorted order
 * by startMinutes at all times using Collections.binarySearch(). This mirrors the
 * exact algorithmic pattern employed in TrainService.confirmedReservations, enabling:
 * - O(log n) position lookup for insertions.
 * - Sequential cache-friendly traversal during overlap detection.
 */
public class Platform {

    /** Comparator to maintain occupiedIntervals chronologically sorted by startMinutes. */
    private static final Comparator<Interval> INTERVAL_START_COMPARATOR =
            Comparator.comparingInt(Interval::getStartMinutes);

    /** Unique platform identifier (e.g., "PF-1", "PF-2", "PF-3"). */
    private final String id;

    /** Physical usable track length in meters (e.g., 300, 400, 500). */
    private final int length;

    /** Chronologically sorted list of occupied intervals on this platform track. */
    private final List<Interval> occupiedIntervals;

    /**
     * Constructs a new Platform instance.
     *
     * @param id     unique identifier for the platform; must not be null
     * @param length physical track length in meters; must be positive
     * @throws NullPointerException     if id is null
     * @throws IllegalArgumentException if length is non-positive
     */
    public Platform(String id, int length) {
        this.id = Objects.requireNonNull(id, "platform id must not be null");
        if (length <= 0) {
            throw new IllegalArgumentException("platform length must be positive: " + length);
     * @param id     unique platform identifier; must not be null
     * @param length physical platform length in meters (> 0)
     * @throws NullPointerException     if id is null
     * @throws IllegalArgumentException if length <= 0
     */
    public Platform(String id, int length) {
        this.id = Objects.requireNonNull(id, "Platform id must not be null");
        if (length <= 0) {
            throw new IllegalArgumentException("Platform length must be positive: " + length);
        }
        this.length = length;
        this.occupiedIntervals = new ArrayList<>();
    }

    /**
     * Determines whether this platform is compatible with the specified train
     * and proposed time window.
     * <p>
     * Compatibility conditions:
     * 1. Physical fit: trainLength must not exceed this platform's usable length.
     * 2. Temporal fit: candidate interval [arrivalMinutes, departureMinutes + bufferMinutes)
     *    must not overlap any currently occupied interval on this platform.
     *
     * @param trainLength      length of the train in meters
     * @param arrivalMinutes   scheduled or adjusted arrival time in minutes since midnight
     * @param departureMinutes scheduled or adjusted departure time in minutes since midnight
     * @param bufferMinutes    safety buffer / clearance time to append after departure in minutes
     * @return true if the train fits physically and the proposed time slot is completely free;
     *         false otherwise
     */
    public boolean isCompatible(int trainLength, int arrivalMinutes, int departureMinutes, int bufferMinutes) {
        // 1. Immediate physical capacity check
     * Returns the platform identifier.
     *
     * @return platform id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the physical platform length in meters.
     *
     * @return length
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns an unmodifiable view of all occupied intervals on this platform.
     *
     * @return unmodifiable list of occupied intervals
     */
    public List<Interval> getOccupiedIntervals() {
        return Collections.unmodifiableList(occupiedIntervals);
    }

    /**
     * Evaluates whether this platform can accommodate a candidate train berthing.
     *
     * Checks:
     * 1. Physical constraint: Train length must not exceed platform length.
     * 2. Temporal constraint: The candidate interval [arrivalMinutes, departureMinutes + bufferMinutes)
     *    must not overlap with any existing occupied interval on this platform.
     *
     * @param trainLength      physical train length in meters
     * @param arrivalMinutes   train arrival time in minutes from midnight
     * @param departureMinutes train departure time in minutes from midnight
     * @param bufferMinutes    clearance buffer time in minutes required after departure
     * @return true if both length and time interval are compatible, false otherwise
     */
    public boolean isCompatible(int trainLength, int arrivalMinutes, int departureMinutes, int bufferMinutes) {
        // Physical length constraint check
        if (trainLength > this.length) {
            return false;
        }

        // 2. Candidate interval incorporating clearance buffer
        Interval candidate = new Interval(arrivalMinutes, departureMinutes + bufferMinutes, null);

        // 3. Collision check against all existing occupied intervals
        for (Interval occupied : occupiedIntervals) {
            if (candidate.overlaps(occupied)) {
        // Temporal interval overlap check
        Interval candidate = new Interval(arrivalMinutes, departureMinutes + bufferMinutes, null);
        for (Interval existing : occupiedIntervals) {
            if (candidate.overlaps(existing)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Inserts an allocated interval into occupiedIntervals at the correct sorted position.
     * <p>
     * NOTE: This method assumes {@link #isCompatible} has already been verified by the caller
     * and returned true. It does not perform re-validation, keeping the insertion simple and fast.
     *
     * @param interval the allocated interval to insert; must not be null
     * @throws NullPointerException if interval is null
     */
    public void occupy(Interval interval) {
        Objects.requireNonNull(interval, "interval must not be null");
     * Inserts an interval into occupiedIntervals maintaining sorted order by startMinutes.
     * PRECONDITION: Call isCompatible() prior to invoking this method.
     *
     * @param interval the Interval to occupy; must not be null
     */
    public void occupy(Interval interval) {
        Objects.requireNonNull(interval, "Interval to occupy cannot be null");
        int index = Collections.binarySearch(occupiedIntervals, interval, INTERVAL_START_COMPARATOR);
        if (index < 0) {
            index = -(index + 1);
        }
        occupiedIntervals.add(index, interval);
    }

    /**
     * Removes the interval belonging to the specified train from occupiedIntervals.
     * Invoked when a train departs, or is delayed/bumped to another slot or platform.
     *
     * @param trainId the unique identifier of the train to release
     * Convenience overload to occupy platform directly using train ID and minute boundaries.
     *
     * @param trainId          the train identifier
     * @param startMinutes     start time in minutes
     * @param endWithBufferMin end time including buffer in minutes
     */
    public void occupy(String trainId, int startMinutes, int endWithBufferMin) {
        occupy(new Interval(startMinutes, endWithBufferMin, trainId));
    }

    /**
     * Releases and removes any occupied intervals belonging to the specified train.
     *
     * @param trainId the identifier of the train to release; must not be null
     */
    public void release(String trainId) {
        if (trainId == null) {
            return;
        }
        occupiedIntervals.removeIf(interval -> trainId.equals(interval.getTrainId()));
    }

    /**
     * Returns the earliest release time (smallest endMinutes) across all current
     * occupied intervals, or {@link Integer#MAX_VALUE} if the platform is currently empty.
     * <p>
     * Used by the scheduling engine to maintain a min-heap of platform availability events.
     *
     * @return the smallest endMinutes among current intervals, or Integer.MAX_VALUE if empty
        occupiedIntervals.removeIf(inv -> trainId.equals(inv.getTrainId()));
    }

    /**
     * Checks if this platform is currently occupied by the specified train.
     *
     * @param trainId the train identifier
     * @return true if an interval with this trainId is currently present
     */
    public boolean isOccupiedBy(String trainId) {
        if (trainId == null) return false;
        for (Interval inv : occupiedIntervals) {
            if (trainId.equals(inv.getTrainId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the earliest endMinutes across all currently occupied intervals.
     * If the platform is currently empty, returns Integer.MAX_VALUE.
     *
     * Used by the scheduling engine to construct a min-heap answering
     * "which platform frees up next".
     *
     * @return smallest endMinutes or Integer.MAX_VALUE if free
     */
    public int getNextReleaseTime() {
        if (occupiedIntervals.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int minEnd = Integer.MAX_VALUE;
        for (Interval interval : occupiedIntervals) {
            if (interval.getEndMinutes() < minEnd) {
                minEnd = interval.getEndMinutes();
        for (Interval inv : occupiedIntervals) {
            if (inv.getEndMinutes() < minEnd) {
                minEnd = inv.getEndMinutes();
            }
        }
        return minEnd;
    }

    /**
     * Returns the unique identifier of the platform.
     *
     * @return platform ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the physical length of the platform in meters.
     *
     * @return length in meters
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns an unmodifiable view of currently occupied intervals.
     *
     * @return unmodifiable sorted list of occupied intervals
     */
    public List<Interval> getOccupiedIntervals() {
        return Collections.unmodifiableList(occupiedIntervals);
    }

    /**
     * Returns a string representation of the platform including its ID, length,
     * and all current occupied intervals.
     *
     * @return formatted platform string
     */
    @Override
    public String toString() {
        return "Platform{" +
                "id='" + id + '\'' +
                ", length=" + length + "m" +
                ", occupiedIntervals=" + occupiedIntervals +
                '}';
     * Formats platform status and occupied intervals into a readable summary string.
     *
     * @return formatted platform description
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Platform %s [Length: %dm] Intervals: ", id, length));
        if (occupiedIntervals.isEmpty()) {
            sb.append("[Idle / Free]");
        } else {
            sb.append(occupiedIntervals);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Platform platform = (Platform) o;
        return length == platform.length && Objects.equals(id, platform.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, length);
    }
}
