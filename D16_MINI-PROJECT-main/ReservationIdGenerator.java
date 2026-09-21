/**
 * ReservationIdGenerator generates sequential reservation identifiers
 * formatted with an 'R' prefix followed by a 3-digit zero-padded number.
 * Examples: R001, R002, ..., R010, R011, etc.
 */
public class ReservationIdGenerator {

    /**
     * Internal counter to track the sequence of reservation IDs.
     * Initialized to 0 as required.
     */
    private int counter = 0;

    /**
     * Default constructor for ReservationIdGenerator.
     */
    public ReservationIdGenerator() {
        this.counter = 0;
    }

    /**
     * Increments the internal counter and returns the next reservation ID
     * in zero-padded format (e.g., R001, R002, R010).
     *
     * @return the next formatted reservation ID
     */
    public String next() {
        counter++;
        return "R%03d".formatted(counter);
    }
}
