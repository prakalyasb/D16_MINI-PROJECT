import java.util.Objects;

/**
 * Payanam Junction - Railway Reservation Prototype
 *
 * Represents a passenger requesting a train ticket reservation.
 * Each passenger is uniquely identified by their passengerId,
 * with requestTime automatically captured upon creation to support
 * First-Come, First-Served (FCFS) or priority-based booking queues.
 */
public class Passenger {

    /** Unique identifier for the passenger (e.g., PNR, user ID, or ticket request token). */
    private final String passengerId;

    /** Full name of the passenger. */
    private final String name;

    /** System timestamp (in milliseconds) when the passenger request was created. */
    private final long requestTime;

    /**
     * Constructs a new Passenger instance.
     * The requestTime is automatically initialized to the current system time in milliseconds.
     *
     * @param passengerId the unique ID of the passenger; must not be null
     * @param name        the name of the passenger; must not be null
     * @throws NullPointerException if passengerId or name is null
     */
    public Passenger(String passengerId, String name) {
        this.passengerId = Objects.requireNonNull(passengerId, "passengerId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.requestTime = System.currentTimeMillis();
    }

    /**
     * Returns the unique passenger ID.
     *
     * @return the passengerId
     */
    public String getPassengerId() {
        return passengerId;
    }

    /**
     * Returns the passenger's name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the timestamp (in milliseconds) when this passenger reservation request was made.
     *
     * @return the requestTime in milliseconds
     */
    public long getRequestTime() {
        return requestTime;
    }

    /**
     * Returns a string representation of the Passenger.
     *
     * @return a string containing passenger details
     */
    @Override
    public String toString() {
        return "Passenger{" +
                "passengerId='" + passengerId + '\'' +
                ", name='" + name + '\'' +
                ", requestTime=" + requestTime +
                '}';
    }

    /**
     * Compares this passenger to another object for equality.
     * Equality is determined strictly by the passengerId field.
     *
     * @param o the object to compare with
     * @return true if both objects represent the same passenger (identical passengerId), false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Passenger passenger = (Passenger) o;
        return Objects.equals(passengerId, passenger.passengerId);
    }

    /**
     * Generates a hash code for this passenger based solely on passengerId.
     *
     * @return the hash code value for this passenger
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(passengerId);
    }
}
