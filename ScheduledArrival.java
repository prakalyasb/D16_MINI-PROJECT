/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Represents a standard scheduled train arrival request running according
 * to standard timetable operations.
 *
 * Priority Rank: 2 (Routine priority level).
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Represents a standard scheduled train arrival request (e.g., daily passenger,
 * express, or mail train service running on regular timetabled routes).
 *
 * Standard priority in the system (priorityRank = 2).
 */
public class ScheduledArrival extends PlatformRequest {

    /**
     * Constructs a new ScheduledArrival request for the given train.
     *
     * @param train the TrainService requesting scheduled platform allocation; must not be null
     * Constructs a new ScheduledArrival request.
     *
     * @param train the TrainService requiring scheduled allocation; must not be null
     */
    public ScheduledArrival(TrainService train) {
        super(train);
    }

    /**
     * Returns the operational priority rank for regular scheduled arrivals.
     * Routine scheduled trains have standard priority level (2).
     * Scheduled arrivals possess standard priority tier (rank 2).
     *
     * @return 2
     */
    @Override
    public int priorityRank() {
        return 2;
    }
}
