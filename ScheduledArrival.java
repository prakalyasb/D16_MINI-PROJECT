/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Represents a standard scheduled train arrival request running according
 * to standard timetable operations.
 *
 * Priority Rank: 2 (Routine priority level).
 */
public class ScheduledArrival extends PlatformRequest {

    /**
     * Constructs a new ScheduledArrival request for the given train.
     *
     * @param train the TrainService requesting scheduled platform allocation; must not be null
     */
    public ScheduledArrival(TrainService train) {
        super(train);
    }

    /**
     * Returns the operational priority rank for regular scheduled arrivals.
     * Routine scheduled trains have standard priority level (2).
     *
     * @return 2
     */
    @Override
    public int priorityRank() {
        return 2;
    }
}
