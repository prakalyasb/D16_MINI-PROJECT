import java.util.Collection;
import java.util.HashMap;

/**
 * RailwaySystem acts as the central service and application coordination layer
 * for managing trains, passengers, and booking workflows.
 *
 * It coordinates TrainService instances, maintains registered passengers,
 * and manages a globally shared ReservationIdGenerator to ensure that reservation
 * IDs remain unique across all trains in the system.
 */
public class RailwaySystem {

    /**
     * Map of trains in the railway system, keyed by train ID.
     */
    private HashMap<String, TrainService> trains;

    /**
     * Shared reservation ID generator used across all trains to guarantee
     * globally unique reservation IDs.
     */
    private ReservationIdGenerator idGenerator;

    /**
     * Map of registered passengers, keyed by passenger ID.
     */
    private HashMap<String, Passenger> passengers;

    /**
     * Constructs a new RailwaySystem, initializing the train and passenger registries
     * and instantiating the shared ReservationIdGenerator.
     */
    public RailwaySystem() {
        this.trains = new HashMap<>();
        this.idGenerator = new ReservationIdGenerator();
        this.passengers = new HashMap<>();
    }

    /**
     * Adds a train service to the railway system.
     * The train is stored using its train ID as the key.
     *
     * @param train the TrainService instance to add
     */
    public void addTrain(TrainService train) {
        if (train == null) {
            throw new IllegalArgumentException("Train cannot be null");
        }
        trains.put(train.getTrainId(), train);
    }

    /**
     * Retrieves a train service by its train ID.
     *
     * @param trainId the identifier of the train to find
     * @return the corresponding TrainService
     * @throws IllegalArgumentException if no train is found with the given ID
     */
    public TrainService getTrain(String trainId) {
        TrainService train = trains.get(trainId);
        if (train == null) {
            throw new IllegalArgumentException("Train not found: " + trainId);
        }
        return train;
    }

    /**
     * Returns a collection view of all trains registered in the system.
     *
     * @return collection of all TrainService instances
     */
    public Collection<TrainService> listAllTrains() {
        return trains.values();
    }

    /**
     * Registers a passenger in the system, keyed by their passenger ID.
     *
     * @param p the Passenger to register
     */
    public void registerPassenger(Passenger p) {
        if (p == null) {
            throw new IllegalArgumentException("Passenger cannot be null");
        }
        passengers.put(p.getId(), p);
    }

    /**
     * Retrieves a passenger by their ID.
     *
     * @param id the passenger identifier
     * @return the Passenger object if present, or null otherwise
     */
    public Passenger getPassenger(String id) {
        return passengers.get(id);
    }

    /**
     * Books a seat on the specified train for a passenger.
     * Looks up the train and passenger; creates and registers the passenger
     * if not already present, or reuses the existing passenger object.
     * Delegates seat allocation and waiting list handling to the TrainService.
     *
     * @param trainId       the ID of the train to book on
     * @param passengerId   the ID of the passenger
     * @param passengerName the name of the passenger (used if creating a new passenger)
     * @return the created Reservation, or null if the passenger was placed on the waiting list
     */
    public Reservation bookSeat(String trainId, String passengerId, String passengerName) {
        TrainService train = getTrain(trainId);
        Passenger passenger = getPassenger(passengerId);

        if (passenger == null) {
            passenger = new Passenger(passengerId, passengerName);
            registerPassenger(passenger);
        }

        return train.bookSeat(passenger, idGenerator);
    }

    /**
     * Cancels a booking on the specified train.
     * Delegates the cancellation logic to the TrainService.
     *
     * @param trainId       the ID of the train where the reservation exists
     * @param reservationId the ID of the reservation to cancel
     * @return the Reservation of any passenger promoted from the waiting list, or null if none
     */
    public Reservation cancelBooking(String trainId, String reservationId) {
        TrainService train = getTrain(trainId);
        return train.cancelBooking(reservationId, idGenerator);
    }
}
