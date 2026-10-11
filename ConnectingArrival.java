/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Represents a connecting train arrival request (e.g., feeder train
 * connecting passengers to onward express services).
 *
 * Priority Rank: 1 (Higher than regular scheduled trains, lower than emergency services).
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Represents a connecting or feeder train arrival request (e.g., passenger
 * connection services with tight cross-platform transfer windows).
 *
 * Second highest priority in the system (priorityRank = 1).
 */
public class ConnectingArrival extends PlatformRequest {

    /**
     * Constructs a new ConnectingArrival request for the given train.
     *
     * @param train the TrainService requesting connecting platform allocation; must not be null
     * Constructs a new ConnectingArrival request.
     *
     * @param train the TrainService requiring connecting allocation; must not be null
     */
    public ConnectingArrival(TrainService train) {
        super(train);
    }

    /**
     * Returns the operational priority rank for connecting train arrivals.
     * Connecting trains have medium-high priority (1) to prevent passenger missed connections.
     * Connecting arrivals possess intermediate priority tier (rank 1).
     *
     * @return 1
     */
    @Override
    public int priorityRank() {
        return 1;
    }
}
