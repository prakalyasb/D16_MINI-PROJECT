/**
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Represents a connecting or feeder train arrival request (e.g., passenger
 * connection services with tight cross-platform transfer windows).
 *
 * Second highest priority in the system (priorityRank = 1).
 */
public class ConnectingArrival extends PlatformRequest {

    /**
     * Constructs a new ConnectingArrival request.
     *
     * @param train the TrainService requiring connecting allocation; must not be null
     */
    public ConnectingArrival(TrainService train) {
        super(train);
    }

    /**
     * Connecting arrivals possess intermediate priority tier (rank 1).
     *
     * @return 1
     */
    @Override
    public int priorityRank() {
        return 1;
    }
}
