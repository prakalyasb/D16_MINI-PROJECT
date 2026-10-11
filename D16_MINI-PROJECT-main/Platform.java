import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
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
        for (Interval inv : occupiedIntervals) {
            if (inv.getEndMinutes() < minEnd) {
                minEnd = inv.getEndMinutes();
            }
        }
        return minEnd;
    }

    /**
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
