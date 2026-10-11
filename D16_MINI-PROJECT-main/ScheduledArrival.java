/**
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Represents a standard scheduled train arrival request (e.g., daily passenger,
 * express, or mail train service running on regular timetabled routes).
 *
 * Standard priority in the system (priorityRank = 2).
 */
public class ScheduledArrival extends PlatformRequest {

    /**
     * Constructs a new ScheduledArrival request.
     *
     * @param train the TrainService requiring scheduled allocation; must not be null
     */
    public ScheduledArrival(TrainService train) {
        super(train);
    }

    /**
     * Scheduled arrivals possess standard priority tier (rank 2).
     *
     * @return 2
     */
    @Override
    public int priorityRank() {
        return 2;
    }
}
