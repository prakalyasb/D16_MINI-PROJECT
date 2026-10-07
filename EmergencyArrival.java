/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Represents an emergency train arrival request (e.g., Medical Relief Special,
 * accident relief train, VIP emergency diversion).
 *
 * Priority Rank: 0 (Highest priority, preempts all other platform requests).
 */
public class EmergencyArrival extends PlatformRequest {

    /**
     * Constructs a new EmergencyArrival request for the given train.
     *
     * @param train the TrainService requesting emergency platform allocation; must not be null
     */
    public EmergencyArrival(TrainService train) {
        super(train);
    }

    /**
     * Returns the operational priority rank for emergency arrivals.
     * Emergency trains are assigned the highest priority level (0).
     *
     * @return 0
     */
    @Override
    public int priorityRank() {
        return 0;
    }
}
