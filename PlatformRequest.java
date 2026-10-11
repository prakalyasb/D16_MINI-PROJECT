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
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Abstract base class representing a train berthing request submitted to the
 * platform allocation engine.
 *
 * DETERMINISTIC THREE-LEVEL TIEBREAK JUSTIFICATION:
 * Under railway operating rules and project requirements, "priority ties must have
 * a deterministic order" so that queue evaluation is stable, reproducible, and strictly fair:
 *
 * Level 1 - Operational Priority Rank (priorityRank() ascending):
 *   Safety-critical and network-sensitive services must preempt regular traffic:
 *   EMERGENCY (0) > CONNECTING (1) > SCHEDULED (2).
 *
 * Level 2 - Scheduled Arrival Time (arrivalMinutes ascending):
 *   Among trains possessing identical priority tier, trains that are due to arrive earlier
 *   must be processed first to avoid bottlenecking downstream track blocks and station approaches.
 *
 * Level 3 - Request Timestamp (requestTime ascending):
 *   If two trains share both the exact same priority class AND identical scheduled arrival times,
 *   FIFO arrival ordering based on request registration timestamp breaks the tie deterministically.
 *   This ensures that no two requests compare as equal unless they are the exact same instance,
 *   eliminating arbitrary ordering behavior in Java's PriorityQueue.
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
     * Deterministic three-level comparator for PriorityQueue ranking.
     */
    public static final Comparator<PlatformRequest> COMPARATOR = (r1, r2) -> {
        // Level 1: Priority rank (0 = Emergency, 1 = Connecting, 2 = Scheduled)
        int pDiff = Integer.compare(r1.priorityRank(), r2.priorityRank());
        if (pDiff != 0) {
            return pDiff;
        }

        // Level 2: Scheduled arrival time in minutes
        int aDiff = Integer.compare(r1.getArrivalMinutes(), r2.getArrivalMinutes());
        if (aDiff != 0) {
            return aDiff;
        }

        // Level 3: Request timestamp (FIFO deterministic tiebreaker)
        return Long.compare(r1.getRequestTime(), r2.getRequestTime());
    };

    /** The TrainService instance for which platform allocation is requested. */
    protected final TrainService train;

    /** System timestamp (in milliseconds) when this allocation request was instantiated. */
    protected final long requestTime;

    /**
     * Constructs a new PlatformRequest.
     *
     * @param train the TrainService requesting allocation; must not be null
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
     * Returns the operational priority rank of this request.
     * Lower integer values indicate higher priority (0 is highest).
     *
     * @return priority rank (0 = Emergency, 1 = Connecting, 2 = Scheduled)
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
     * Returns the associated TrainService.
     *
     * @return train
     */
    public TrainService getTrain() {
        return train;
    }

    /**
     * Returns the identifier of the associated train.
     *
     * @return trainId
     */
    public String getTrainId() {
        return train.getTrainId();
    }

    /**
     * Returns the creation timestamp of this request.
     *
     * @return requestTime in milliseconds
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
     * Converts the train's stored arrival time (HH:MM) into minutes from midnight.
     *
     * @return arrival time in minutes (0 to 1439)
     */
    public int getArrivalMinutes() {
        return parseTimeToMinutes(train.getArrivalTime());
    }

    /**
     * Converts the train's stored departure time (HH:MM) into minutes from midnight.
     *
     * @return departure time in minutes (0 to 1439)
     */
    public int getDepartureMinutes() {
        return parseTimeToMinutes(train.getDepartureTime());
    }

    /**
     * Helper to parse "HH:MM" into total minutes from midnight.
     *
     * @param timeString time in "HH:MM" format
     * @return total minutes from midnight
     */
    public static int parseTimeToMinutes(String timeString) {
        if (timeString == null || !timeString.contains(":")) {
            return 0;
        }
        try {
            String[] parts = timeString.trim().split(":");
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            return h * 60 + m;
        } catch (Exception e) {
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
        return String.format("%s [Train: %s, Priority: %d, Arr: %s, Dep: %s, ReqTime: %d]",
                getClass().getSimpleName(),
                train.getTrainId(),
                priorityRank(),
                train.getArrivalTime(),
                train.getDepartureTime(),
                requestTime);
    }
}
