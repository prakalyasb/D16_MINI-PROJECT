import java.util.List;
import java.util.Objects;

/**
 * Payanam Junction - Platform Allocation Subsystem
 *
 * DelayHandler encapsulates the business rules and state transitions for trains
 * whose arrival or departure schedules are modified dynamically.
 *
 * INVARIANT GUARANTEE:
 * A train undergoing delay handling must NEVER be left in an indeterminate or
 * "between states" limbo. Upon completion of handleDelay(), the train is mathematically
 * guaranteed to reside in exactly one of two valid operational states:
 *   1. Berthing state: The train holds a valid, compatible, occupied Platform.
 *   2. Waiting state: The train's request is safely enqueued in the PlatformAllocationEngine's
 *      deterministic priority waiting queue.
 */
public class DelayHandler {

    /** Tracks the platform assigned during the most recent handleDelay invocation (or null if queued). */
    private static Platform lastResultPlatform = null;

    /**
     * Returns the platform held by the train following the last handleDelay execution,
     * or null if the train was placed into the waiting queue.
     *
     * @return resulting Platform or null
     */
    public static Platform getLastResultPlatform() {
        return lastResultPlatform;
    }

    /**
     * Processes a schedule delay for a given train service.
     *
     * Handles:
     * - Waiting trains: Updates schedule and re-ranks in PriorityQueue.
     * - Berthed trains: Temporarily releases old interval to evaluate compatibility at new times.
     *   - If compatible on same platform: re-occupies platform at new times.
     *   - If incompatible: attempts global allocation via engine.tryAllocate(). If a new platform
     *     is available, assigns it; otherwise tryAllocate automatically places the request into
     *     waitingTrains.
     *
     * @param train               the TrainService being delayed (must not be null)
     * @param newArrivalMinutes   new arrival time in minutes from midnight (00:00 = 0)
     * @param newDepartureMinutes new departure time in minutes from midnight
     * @param currentPlatform     platform currently held by the train, or null if waiting
     * @param engine              allocation engine managing platforms and queue (must not be null)
     * @param bufferMinutes       clearance buffer required between consecutive berthings
     */
    public static void handleDelay(TrainService train, int newArrivalMinutes, int newDepartureMinutes,
                                   Platform currentPlatform, PlatformAllocationEngine engine, int bufferMinutes) {
        Objects.requireNonNull(train, "train must not be null");
        Objects.requireNonNull(engine, "engine must not be null");

        if (newArrivalMinutes < 0 || newDepartureMinutes < newArrivalMinutes) {
            throw new IllegalArgumentException(String.format(
                    "Invalid schedule times: arrival=%d, departure=%d", newArrivalMinutes, newDepartureMinutes));
        }

        String newArrHHMM = formatMinutesToHHMM(newArrivalMinutes);
        String newDepHHMM = formatMinutesToHHMM(newDepartureMinutes);

        // Case 1: Train currently holds NO platform (still waiting in priority queue)
        if (currentPlatform == null) {
            // Update the train's stored arrival and departure times
            train.setSchedule(newArrHHMM, newDepHHMM, train.getTrainLength(), train.getDeclaredPriority());

            // Locate existing PlatformRequest in waiting queue and notify engine to re-rank
            PlatformRequest existingRequest = findExistingRequest(engine, train.getTrainId());
            if (existingRequest != null) {
                engine.reRank(existingRequest);
            }

            lastResultPlatform = null;
            System.out.printf("Train %s is waiting; updated schedule to %s-%s and re-ranked in waiting queue.%n",
                    train.getTrainId(), newArrHHMM, newDepHHMM);

            // Invariant confirmation for Case 1
            assert isTrainInWaitingQueue(engine, train.getTrainId()) :
                    "Invariant Violation: Waiting train " + train.getTrainId() + " must be present in queue";
            return;
        }

        // Case 2: Train DOES hold a platform
        // First, call release to free its old interval so we can test compatibility cleanly
        currentPlatform.release(train.getTrainId());

        // Update train's stored arrival and departure times
        train.setSchedule(newArrHHMM, newDepHHMM, train.getTrainLength(), train.getDeclaredPriority());

        // Check if the platform remains compatible at the new interval with own old interval removed
        boolean stillCompatible = currentPlatform.isCompatible(
                train.getTrainLength(), newArrivalMinutes, newDepartureMinutes, bufferMinutes);

        if (stillCompatible) {
            // Still compatible: re-occupy at the new interval — train keeps platform
            int endWithBuffer = newDepartureMinutes + bufferMinutes;
            currentPlatform.occupy(new Interval(newArrivalMinutes, endWithBuffer, train.getTrainId()));
            lastResultPlatform = currentPlatform;
            System.out.printf("Train %s delayed, kept Platform %s.%n", train.getTrainId(), currentPlatform.getId());

            // Invariant confirmation for kept platform
            assert lastResultPlatform != null && currentPlatform.isOccupiedBy(train.getTrainId()) :
                    "Invariant Violation: Train " + train.getTrainId() + " must be occupied on kept platform";
        } else {
            // NOT compatible: do NOT put it back.
            // Build fresh PlatformRequest and attempt allocation across all platforms
            PlatformRequest freshRequest = createPlatformRequest(train);
            Platform allocatedPlatform = engine.tryAllocate(
                    freshRequest, train.getTrainLength(), newArrivalMinutes, newDepartureMinutes, bufferMinutes);

            if (allocatedPlatform != null) {
                // Succeeded in finding another compatible platform
                lastResultPlatform = allocatedPlatform;
                System.out.printf("Train %s delayed, reassigned to Platform %s.%n",
                        train.getTrainId(), allocatedPlatform.getId());

                // Invariant confirmation for reassigned platform
                assert lastResultPlatform != null && allocatedPlatform.isOccupiedBy(train.getTrainId()) :
                        "Invariant Violation: Train " + train.getTrainId() + " must be occupied on reassigned platform";
            } else {
                // Could not fit on any platform; tryAllocate automatically added to waitingTrains
                lastResultPlatform = null;
                System.out.printf("Train %s delayed, lost Platform %s, re-queued for allocation.%n",
                        train.getTrainId(), currentPlatform.getId());

                // Invariant confirmation for re-queued train
                assert isTrainInWaitingQueue(engine, train.getTrainId()) :
                        "Invariant Violation: Train " + train.getTrainId() + " must be present in waiting queue";
            }
        }

        /*
         * FINAL INVARIANT CHECK:
         * A train must NEVER be left between states. It must strictly hold a platform OR be in the waiting queue.
         */
        boolean holdsPlatform = (lastResultPlatform != null);
        boolean inWaitingQueue = isTrainInWaitingQueue(engine, train.getTrainId());
        if (!holdsPlatform && !inWaitingQueue) {
            throw new IllegalStateException("FATAL INVARIANT VIOLATION: Train " + train.getTrainId() +
                    " was left in an unallocated limbo state!");
        }
    }

    /**
     * Factory method creating the appropriate PlatformRequest subclass based on
     * the train's declared priority.
     *
     * @param train the TrainService
     * @return EmergencyArrival, ConnectingArrival, or ScheduledArrival
     */
    public static PlatformRequest createPlatformRequest(TrainService train) {
        String priority = train.getDeclaredPriority();
        if (priority != null) {
            if ("EMERGENCY".equalsIgnoreCase(priority)) {
                return new EmergencyArrival(train);
            } else if ("CONNECTING".equalsIgnoreCase(priority)) {
                return new ConnectingArrival(train);
            }
        }
        return new ScheduledArrival(train);
    }

    /**
     * Locates an existing PlatformRequest for the given train in the engine's waiting queue.
     *
     * @param engine  allocation engine
     * @param trainId train ID
     * @return PlatformRequest or null
     */
    private static PlatformRequest findExistingRequest(PlatformAllocationEngine engine, String trainId) {
        List<PlatformRequest> snapshot = engine.getWaitingSnapshot();
        for (PlatformRequest req : snapshot) {
            if (req != null && trainId.equals(req.getTrainId())) {
                return req;
            }
        }
        return null;
    }

    /**
     * Checks if a train is currently present in the engine's waiting queue.
     *
     * @param engine  allocation engine
     * @param trainId train ID
     * @return true if waiting, false otherwise
     */
    private static boolean isTrainInWaitingQueue(PlatformAllocationEngine engine, String trainId) {
        List<PlatformRequest> snapshot = engine.getWaitingSnapshot();
        for (PlatformRequest req : snapshot) {
            if (req != null && trainId.equals(req.getTrainId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts minutes from midnight to HH:MM format.
     *
     * @param minutes minutes
     * @return HH:MM string
     */
    public static String formatMinutesToHHMM(int minutes) {
        int h = (minutes / 60) % 24;
        int m = minutes % 60;
        return String.format("%02d:%02d", h, m);
    }
}
