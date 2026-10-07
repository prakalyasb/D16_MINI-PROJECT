import java.util.Comparator;
import java.util.Objects;

/**
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
     * Returns the operational priority rank of this request.
     * Lower integer values indicate higher priority (0 is highest).
     *
     * @return priority rank (0 = Emergency, 1 = Connecting, 2 = Scheduled)
     */
    public abstract int priorityRank();

    /**
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
        return String.format("%s [Train: %s, Priority: %d, Arr: %s, Dep: %s, ReqTime: %d]",
                getClass().getSimpleName(),
                train.getTrainId(),
                priorityRank(),
                train.getArrivalTime(),
                train.getDepartureTime(),
                requestTime);
    }
}
