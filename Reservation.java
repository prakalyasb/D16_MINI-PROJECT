import java.util.Objects;

/**
 * Payanam Junction - Railway Reservation Prototype
 *
 * Represents a confirmed or cancelled railway reservation.
 * Tracks the assigned seat, booking timestamp, passenger details,
 * and current reservation status.
 */
public class Reservation {

    /**
     * Enumeration representing the possible states of a reservation.
     */
    public enum Status {
        CONFIRMED,
        CANCELLED
    }

    /** Unique identifier for this reservation. */
    private final String reservationId;

    /** Passenger associated with this reservation. */
    private final Passenger passenger;

    /** Identifier for the train. */
    private final String trainId;

    /** Allocated seat number on the train. */
    private final int seatNumber;

    /** System timestamp (in milliseconds) when the booking was confirmed. */
    private final long bookingTime;

    /** Current status of the reservation (defaults to CONFIRMED). */
    private Status status;

    /**
     * Constructs a new Reservation.
     * Initializes bookingTime to the current system time and sets status to CONFIRMED.
     *
     * @param reservationId unique identifier for the reservation; must not be null
     * @param passenger     the passenger for whom the ticket is booked; must not be null
     * @param trainId       the identifier of the train; must not be null
     * @param seatNumber    the assigned seat number
     * @throws NullPointerException if reservationId, passenger, or trainId is null
     */
    public Reservation(String reservationId, Passenger passenger, String trainId, int seatNumber) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.passenger = Objects.requireNonNull(passenger, "passenger must not be null");
        this.trainId = Objects.requireNonNull(trainId, "trainId must not be null");
        this.seatNumber = seatNumber;
        this.bookingTime = System.currentTimeMillis();
        this.status = Status.CONFIRMED;
    }

    /**
     * Updates the reservation status to CANCELLED.
     */
    public void markCancelled() {
        this.status = Status.CANCELLED;
    }

    /**
     * Returns the unique reservation ID.
     *
     * @return the reservationId
     */
    public String getReservationId() {
        return reservationId;
    }

    /**
     * Returns the passenger associated with this reservation.
     *
     * @return the passenger
     */
    public Passenger getPassenger() {
        return passenger;
    }

    /**
     * Returns the train ID for this reservation.
     *
     * @return the trainId
     */
    public String getTrainId() {
        return trainId;
    }

    /**
     * Returns the allocated seat number.
     *
     * @return the seatNumber
     */
    public int getSeatNumber() {
        return seatNumber;
    }

    /**
     * Returns the timestamp (in milliseconds) when the booking was made.
     *
     * @return the bookingTime in milliseconds
     */
    public long getBookingTime() {
        return bookingTime;
    }

    /**
     * Returns the current status of the reservation.
     *
     * @return the reservation status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns a string representation of the Reservation.
     *
     * @return a string containing reservation details
     */
    @Override
    public String toString() {
        return "Reservation{" +
                "reservationId='" + reservationId + '\'' +
                ", passenger=" + passenger +
                ", trainId='" + trainId + '\'' +
                ", seatNumber=" + seatNumber +
                ", bookingTime=" + bookingTime +
                ", status=" + status +
                '}';
    }

    /**
     * Compares this reservation to another object for equality based on reservationId.
     *
     * @param o the object to compare with
     * @return true if both reservations have the same reservationId, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Reservation that = (Reservation) o;
        return Objects.equals(reservationId, that.reservationId);
    }

    /**
     * Generates a hash code based on reservationId.
     *
     * @return the hash code value for this reservation
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(reservationId);
    }
}
