import java.util.Comparator;
import java.util.Objects;

/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Abstract base class representing a platform allocation request submitted for a train.
 * Serves as the priority item for the PriorityQueue-based platform allocation engine.
 *
 * Architectural & Data Structure Justifications:
 * - WHY the Comparator uses three tiebreak levels:
 *   1. Priority Rank (Primary): Enforces operational precedence — Emergency trains (rank 0)
 *      must preempt Connecting trains (rank 1), which in turn preempt Scheduled trains (rank 2),
 *      guaranteeing safety and critical connection transfers.
 *   2. Scheduled Arrival Time in minutes (Secondary): When two trains have identical priority
 *      (e.g., two routine Scheduled arrivals), the train with the earlier scheduled arrival time
 *      is allocated first to preserve timetable integrity and prevent cascading network delays.
 *   3. Request Time in milliseconds (Tertiary Deterministic Tiebreaker): In Java, {@link java.util.PriorityQueue}
 *      does not provide FIFO or deterministic tie-breaking for elements with equivalent comparator values.
 *      If two trains have the same priority rank AND identical scheduled arrival times (e.g., concurrent
 *      08:30 arrivals), order would be arbitrary and non-deterministic without this level. Using the
 *      system timestamp recorded at request creation guarantees strict, reproducible FIFO ordering,
 *      fulfilling the project requirement that priority ties must never be left ambiguous.
 */
public abstract class PlatformRequest {

    /**
     * Three-tier deterministic Comparator for ordering PlatformRequest instances in a PriorityQueue.
     * Tier 1: priorityRank() ascending (0 = Emergency, 1 = Connecting, 2 = Scheduled)
     * Tier 2: scheduled arrival time in minutes since midnight ascending
     * Tier 3: requestTime in milliseconds ascending (deterministic FIFO tiebreaker)
     */
    public static final Comparator<PlatformRequest> PRIORITY_COMPARATOR =
            Comparator.comparingInt(PlatformRequest::priorityRank)
                    .thenComparingInt(PlatformRequest::getScheduledArrivalMinutes)
                    .thenComparingLong(PlatformRequest::getRequestTime);

    /** Alias for PRIORITY_COMPARATOR for flexible integration across teammates' engines. */
    public static final Comparator<PlatformRequest> COMPARATOR = PRIORITY_COMPARATOR;

    /** The train service associated with this allocation request. */
    private final TrainService train;

    /** System timestamp (in milliseconds) when this request was created. */
    private final long requestTime;

    /**
     * Constructs a new PlatformRequest for the specified train.
     * Captures current system time as the deterministic tiebreaker timestamp.
     *
     * @param train the TrainService requesting platform allocation; must not be null
     * @throws NullPointerException if train is null
     */
    public PlatformRequest(TrainService train) {
        this.train = Objects.requireNonNull(train, "train must not be null");
        this.requestTime = System.currentTimeMillis();
    }

    /**
     * Returns the numeric priority rank for this request type.
     * Lower integer value denotes higher operational priority (served earlier).
     *
     * @return priority rank (0 for Emergency, 1 for Connecting, 2 for Scheduled)
     */
    public abstract int priorityRank();

    /**
     * Returns the scheduled arrival time of the train converted to minutes since midnight.
     * Parses the string format from {@link TrainService#getArrivalTime()}.
     *
     * @return scheduled arrival time in minutes since midnight
     */
    public int getScheduledArrivalMinutes() {
        return parseTimeToMinutes(train.getArrivalTime());
    }

    /**
     * Returns the train service associated with this request.
     *
     * @return TrainService instance
     */
    public TrainService getTrain() {
        return train;
    }

    /**
     * Returns the timestamp (in milliseconds) when this request was created.
     *
     * @return request creation timestamp
     */
    public long getRequestTime() {
        return requestTime;
    }

    /**
     * Utility method to parse a time string (e.g., "08:30", "8:30", "14:15", "08:30 AM")
     * into the number of minutes since midnight.
     *
     * @param timeStr formatted time string
     * @return minutes since midnight, or 0 if timeStr is null or blank
     */
    public static int parseTimeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0;
        }

        String s = timeStr.trim().toUpperCase();
        boolean isPm = s.endsWith("PM");
        boolean isAm = s.endsWith("AM");
        if (isPm || isAm) {
            s = s.substring(0, s.length() - 2).trim();
        }

        String[] parts = s.split(":");
        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;

            if (isPm && hours < 12) {
                hours += 12;
            } else if (isAm && hours == 12) {
                hours = 0;
            }

            return hours * 60 + minutes;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "trainId='" + train.getTrainId() + '\'' +
                ", arrivalTime=" + train.getArrivalTime() +
                " (" + getScheduledArrivalMinutes() + " mins)" +
                ", priorityRank=" + priorityRank() +
                ", requestTime=" + requestTime +
                '}';
    }
}
