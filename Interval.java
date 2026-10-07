import java.util.Objects;

/**
 * Payanam Junction - Railway Reservation System
 * Platform Allocation Module
 *
 * Interval represents a continuous time block allocated to a train on a platform,
 * modeled as a half-open interval [startMinutes, endMinutes).
 *
 * In half-open interval semantics:
 * - The start time is inclusive and the end time is exclusive.
 * - Two intervals that touch at endpoints (e.g., [08:00, 08:30) and [08:30, 09:00))
 *   do NOT overlap, meaning a subsequent train can occupy the platform the instant
 *   the previous train's allocated interval finishes.
 */
public class Interval {

    /** Start time in minutes from midnight (inclusive). */
    private final int startMinutes;

    /** End time in minutes from midnight (exclusive). */
    private final int endMinutes;

    /**
     * Unique identifier of the occupying train.
     * Can be null when this interval is used as an unassigned candidate or probe interval.
     */
    private final String trainId;

    /**
     * Constructs a new Interval instance.
     *
     * @param startMinutes start time in minutes since midnight (inclusive, >= 0)
     * @param endMinutes   end time in minutes since midnight (exclusive, >= startMinutes)
     * @param trainId      identifier of the train, or null if an unassigned candidate interval
     * @throws IllegalArgumentException if startMinutes is negative or endMinutes is before startMinutes
     */
    public Interval(int startMinutes, int endMinutes, String trainId) {
        if (startMinutes < 0) {
            throw new IllegalArgumentException("startMinutes must be non-negative: " + startMinutes);
        }
        if (endMinutes < startMinutes) {
            throw new IllegalArgumentException(
                    "endMinutes (" + endMinutes + ") cannot be before startMinutes (" + startMinutes + ")");
        }
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.trainId = trainId;
    }

    /**
     * Checks whether this interval overlaps with another interval.
     * <p>
     * Follows half-open interval semantics [start, end). Two intervals overlap if and only
     * if they share non-zero time in common. Touching endpoints (e.g., this.end == other.start)
     * do NOT count as overlapping.
     *
     * @param other the other interval to check against; must not be null
     * @return true if both intervals overlap, false otherwise
     * @throws NullPointerException if other is null
     */
    public boolean overlaps(Interval other) {
        Objects.requireNonNull(other, "other interval must not be null");
        return this.startMinutes < other.endMinutes && other.startMinutes < this.endMinutes;
    }

    /**
     * Returns the start time in minutes since midnight.
     *
     * @return startMinutes (inclusive)
     */
    public int getStartMinutes() {
        return startMinutes;
    }

    /**
     * Returns the end time in minutes since midnight.
     *
     * @return endMinutes (exclusive)
     */
    public int getEndMinutes() {
        return endMinutes;
    }

    /**
     * Returns the unique identifier of the train occupying this interval.
     *
     * @return the trainId, or null if unassigned
     */
    public String getTrainId() {
        return trainId;
    }

    /**
     * Formats the interval as a human-readable HH:MM-HH:MM string
     * converting total minutes from midnight into 24-hour clock representation.
     *
     * @return formatted time range string (e.g., "08:30-08:45")
     */
    @Override
    public String toString() {
        int startH = startMinutes / 60;
        int startM = startMinutes % 60;
        int endH = endMinutes / 60;
        int endM = endMinutes % 60;
        return String.format("%02d:%02d-%02d:%02d", startH, startM, endH, endM);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interval interval = (Interval) o;
        return startMinutes == interval.startMinutes &&
                endMinutes == interval.endMinutes &&
                Objects.equals(trainId, interval.trainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startMinutes, endMinutes, trainId);
    }
}
