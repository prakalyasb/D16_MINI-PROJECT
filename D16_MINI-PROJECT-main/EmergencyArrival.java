/**
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Represents an emergency train arrival request (e.g., Medical Relief Special,
 * breakdown clearance trains, or emergency route rerouting).
 *
 * Highest possible priority in the system (priorityRank = 0).
 */
public class EmergencyArrival extends PlatformRequest {

    /**
     * Constructs a new EmergencyArrival request.
     *
     * @param train the TrainService requiring emergency allocation; must not be null
     */
    public EmergencyArrival(TrainService train) {
        super(train);
    }

    /**
     * Emergency arrivals possess the highest priority tier (rank 0).
     *
     * @return 0
     */
    @Override
    public int priorityRank() {
        return 0;
    }
}
