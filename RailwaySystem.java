import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RailwaySystem acts as the central service and application coordination layer
 * for managing trains, passengers, booking workflows, platform allocations,
 * and train delay operations.
 *
 * It coordinates TrainService instances, maintains registered passengers,
 * manages the global ReservationIdGenerator, and integrates the PlatformAllocationEngine
 * and DelayHandler subsystems.
 */
public class RailwaySystem {

    /** Default buffer clearance time (in minutes) required after each train departure. */
    public static final int DEFAULT_BUFFER_MINUTES = 5;

    /** Map of trains in the railway system, keyed by train ID. */
    private HashMap<String, TrainService> trains;

    /** Shared reservation ID generator used across all trains. */
    private ReservationIdGenerator idGenerator;

    /** Map of registered passengers, keyed by passenger ID. */
    private HashMap<String, Passenger> passengers;

    /** Station platforms (PF-1 300m, PF-2 400m, PF-3 500m). */
    private List<Platform> platforms;

    /** Platform allocation and priority queue management engine. */
    private PlatformAllocationEngine allocationEngine;

    /** Map tracking which platform, if any, each train currently holds (keyed by train ID). */
    private Map<String, Platform> trainToPlatform;

    /** Buffer clearance time in minutes between consecutive platform occupations. */
    private int bufferMinutes;

    /**
     * Constructs a new RailwaySystem, initializing the train and passenger registries,
     * physical platforms, allocation engine, and platform tracking map.
     */
    public RailwaySystem() {
        this.trains = new HashMap<>();
        this.idGenerator = new ReservationIdGenerator();
        this.passengers = new HashMap<>();

        // Initialize 3 physical platforms with lengths 300m, 400m, 500m
        this.platforms = new ArrayList<>();
        this.platforms.add(new Platform("PF-1", 300));
        this.platforms.add(new Platform("PF-2", 400));
        this.platforms.add(new Platform("PF-3", 500));

        this.bufferMinutes = DEFAULT_BUFFER_MINUTES;
        this.allocationEngine = new PlatformAllocationEngine(this.platforms);
        this.trainToPlatform = new HashMap<>();
    }

    /**
     * Adds a train service to the railway system.
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
        passengers.put(p.getPassengerId(), p);
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
     *
     * @param trainId       the ID of the train to book on
     * @param passengerId   the ID of the passenger
     * @param passengerName the name of the passenger
     * @return the created Reservation, or null if waitlisted
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
     *
     * @param trainId       the ID of the train
     * @param reservationId the ID of the reservation to cancel
     * @return promoted Reservation or null
     */
    public Reservation cancelBooking(String trainId, String reservationId) {
        TrainService train = getTrain(trainId);
        return train.cancelBooking(reservationId, idGenerator);
    }

    // =========================================================================
    // Platform Allocation & Delay Handling Methods
    // =========================================================================

    /**
     * Requests platform allocation for a specified train based on its declared priority
     * and physical/time constraints.
     *
     * Builds the corresponding PlatformRequest subclass based on the train's declared priority:
     * - EMERGENCY -> EmergencyArrival
     * - CONNECTING -> ConnectingArrival
     * - SCHEDULED -> ScheduledArrival
     *
     * @param trainId the identifier of the train requesting a platform
     * @return the allocated Platform, or null if added to the waiting queue
     */
    public Platform requestPlatform(String trainId) {
        TrainService train = getTrain(trainId);

        // If train already holds a platform, return it
        if (trainToPlatform.containsKey(trainId)) {
            return trainToPlatform.get(trainId);
        }

        int arrivalMinutes = parseTimeToMinutes(train.getArrivalTime());
        int departureMinutes = parseTimeToMinutes(train.getDepartureTime());

        PlatformRequest request = DelayHandler.createPlatformRequest(train);
        Platform allocated = allocationEngine.tryAllocate(
                request, train.getTrainLength(), arrivalMinutes, departureMinutes, bufferMinutes);

        if (allocated != null) {
            trainToPlatform.put(trainId, allocated);
        }
        return allocated;
    }

    /**
     * Departs a train from its allocated platform, frees the platform interval,
     * and iteratively retries allocation for waiting trains in the priority queue.
     *
     * @param trainId the identifier of the departing train
     * @throws IllegalArgumentException if the train is not found or does not hold a platform
     */
    public void departTrain(String trainId) {
        TrainService train = getTrain(trainId);
        Platform platform = trainToPlatform.get(trainId);

        if (platform == null) {
            throw new IllegalArgumentException("Train " + trainId + " does not currently hold a platform.");
        }

        // Release the platform interval and remove active tracking
        platform.release(trainId);
        trainToPlatform.remove(trainId);
        System.out.printf("Train %s departed. Platform %s freed.%n", trainId, platform.getId());

        // Iteratively retry placing waiting trains now that capacity was released
        Platform newlyAllocated;
        while ((newlyAllocated = allocationEngine.retryNextWaiting(bufferMinutes)) != null) {
            // Identify which waiting train was placed onto the newlyAllocated platform
            String promotedTrainId = null;
            for (Interval inv : newlyAllocated.getOccupiedIntervals()) {
                if (!trainToPlatform.containsKey(inv.getTrainId())) {
                    promotedTrainId = inv.getTrainId();
                    break;
                }
            }

            if (promotedTrainId != null) {
                trainToPlatform.put(promotedTrainId, newlyAllocated);
                System.out.printf("Waiting Train %s promoted and allocated to Platform %s.%n",
                        promotedTrainId, newlyAllocated.getId());
            } else {
                System.out.printf("A waiting train was successfully allocated to Platform %s.%n",
                        newlyAllocated.getId());
            }
        }
    }

    /**
     * Handles a schedule delay for the specified train by converting HH:MM inputs,
     * retrieving current platform assignment (if any), delegating to DelayHandler.handleDelay(),
     * and updating internal platform tracking based on the result.
     *
     * @param trainId          the identifier of the train being delayed
     * @param newArrivalHHMM   the new arrival time in "HH:MM" format
     * @param newDepartureHHMM the new departure time in "HH:MM" format
     */
    public void delayTrain(String trainId, String newArrivalHHMM, String newDepartureHHMM) {
        TrainService train = getTrain(trainId);
        int newArrivalMinutes = parseTimeToMinutes(newArrivalHHMM);
        int newDepartureMinutes = parseTimeToMinutes(newDepartureHHMM);

        Platform currentPlatform = trainToPlatform.get(trainId);

        // Delegate to DelayHandler
        DelayHandler.handleDelay(
                train, newArrivalMinutes, newDepartureMinutes, currentPlatform, allocationEngine, bufferMinutes);

        // Synchronize trainToPlatform map with DelayHandler outcome
        Platform resultPlatform = DelayHandler.getLastResultPlatform();
        if (resultPlatform != null) {
            trainToPlatform.put(trainId, resultPlatform);
        } else {
            trainToPlatform.remove(trainId);
        }
    }

    /**
     * Returns an unmodifiable list of station platforms.
     *
     * @return unmodifiable list of platforms
     */
    public List<Platform> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }

    /**
     * Returns the allocation engine.
     *
     * @return PlatformAllocationEngine instance
     */
    public PlatformAllocationEngine getAllocationEngine() {
        return allocationEngine;
    }

    /**
     * Returns an unmodifiable map of train-to-platform assignments.
     *
     * @return unmodifiable map
     */
    public Map<String, Platform> getTrainToPlatform() {
        return Collections.unmodifiableMap(trainToPlatform);
    }

    /**
     * Returns the platform currently held by a train, or null if none.
     *
     * @param trainId the train identifier
     * @return assigned Platform or null
     */
    public Platform getAssignedPlatform(String trainId) {
        return trainToPlatform.get(trainId);
    }

    /**
     * Returns the clearance buffer in minutes.
     *
     * @return bufferMinutes
     */
    public int getBufferMinutes() {
        return bufferMinutes;
    }

    /**
     * Sets the clearance buffer in minutes.
     *
     * @param bufferMinutes clearance buffer
     */
    public void setBufferMinutes(int bufferMinutes) {
        this.bufferMinutes = bufferMinutes;
    }

    /**
     * Helper to parse a 24-hour "HH:MM" string into minutes from midnight.
     *
     * @param hhmm time string
     * @return total minutes from midnight
     * @throws IllegalArgumentException if format is invalid
     */
    public static int parseTimeToMinutes(String hhmm) {
        if (hhmm == null || !hhmm.contains(":")) {
            throw new IllegalArgumentException("Time must be in HH:MM format (got: " + hhmm + ")");
        }
        String[] parts = hhmm.trim().split(":");
        int hours = Integer.parseInt(parts[0].trim());
        int mins = Integer.parseInt(parts[1].trim());
        if (hours < 0 || hours > 23 || mins < 0 || mins > 59) {
            throw new IllegalArgumentException("Hours must be 0-23 and minutes 0-59 (got: " + hhmm + ")");
        }
        return hours * 60 + mins;
    }

    /**
     * Helper to format total minutes into a 24-hour "HH:MM" string.
     *
     * @param minutes minutes from midnight
     * @return HH:MM string
     */
    public static String formatMinutesToTime(int minutes) {
        int hours = (minutes / 60) % 24;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }
}
