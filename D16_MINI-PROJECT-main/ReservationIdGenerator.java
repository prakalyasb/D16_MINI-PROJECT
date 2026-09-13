import java.util.concurrent.atomic.AtomicInteger;

/**
 * Payanam Junction - Railway Reservation Prototype
 *
 * Generates sequential reservation IDs formatted with an 'R' prefix
 * followed by a zero-padded integer (e.g., R001, R002, R003, ...).
 */
public class ReservationIdGenerator {

    /** Atomic counter ensuring thread-safe sequential ID generation starting from 0. */
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Generates and returns the next sequential reservation ID.
     *
     * @return the next sequential reservation ID (e.g., "R001", "R002", "R003")
     */
    public String next() {
        return String.format("R%03d", counter.incrementAndGet());
    }
}
