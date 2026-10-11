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
 * Payanam Junction - Platform Allocation Subsystem
 *
 * Interval represents a continuous time block [startMinutes, endMinutes)
 * during which a platform track is occupied by a designated train service.
 *
 * Mathematical semantics:
 * Half-open interval convention [start, end) is used throughout the system.
 * Two intervals overlap if and only if they share a common interior time point.
 * Touching endpoints (e.g. interval A ends at 10:00 and interval B starts at 10:00)
 * do NOT count as overlapping, permitting seamless transition between consecutive berthings.
 */
public class Interval {

    /** Start time in minutes from midnight (00:00 = 0). */
    private final int startMinutes;

    /** End time in minutes from midnight (inclusive of dwell time and clearance buffer). */
    private final int endMinutes;

    /** Unique identifier of the train occupying this interval, or null for candidate queries. */
    private final String trainId;

    /**
     * Constructs a new Interval.
     *
     * @param startMinutes start time in minutes from midnight (>= 0)
     * @param endMinutes   end time in minutes from midnight (>= startMinutes)
     * @param trainId      train identifier occupying this interval (may be null for temporary candidates)
     * @throws IllegalArgumentException if startMinutes < 0 or endMinutes < startMinutes
     */
    public Interval(int startMinutes, int endMinutes, String trainId) {
        if (startMinutes < 0) {
            throw new IllegalArgumentException("startMinutes cannot be negative: " + startMinutes);
        }
        if (endMinutes < startMinutes) {
            throw new IllegalArgumentException(String.format(
                    "endMinutes (%d) cannot be earlier than startMinutes (%d)", endMinutes, startMinutes));
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
     * Returns the start time of the interval in minutes from midnight.
     *
     * @return startMinutes
     */
    public int getStartMinutes() {
        return startMinutes;
    }

    /**
     * Returns the end time in minutes since midnight.
     *
     * @return endMinutes (exclusive)
     * Returns the end time of the interval in minutes from midnight.
     *
     * @return endMinutes
     */
    public int getEndMinutes() {
        return endMinutes;
    }

    /**
     * Returns the unique identifier of the train occupying this interval.
     *
     * @return the trainId, or null if unassigned
     * Returns the identifier of the occupying train.
     *
     * @return trainId (or null if temporary candidate)
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
     * Checks whether this interval overlaps with another interval using half-open [start, end) semantics.
     * Touching endpoints do NOT count as overlapping:
     * e.g., [08:00, 08:30) and [08:30, 09:00) return false.
     *
     * @param other the other interval to compare against
     * @return true if both intervals share interior time points, false otherwise
     */
    public boolean overlaps(Interval other) {
        if (other == null) {
            return false;
        }
        return this.startMinutes < other.endMinutes && other.startMinutes < this.endMinutes;
    }

    /**
     * Helper method to convert minutes from midnight to a 24-hour "HH:MM" string.
     *
     * @param minutes minutes from midnight
     * @return formatted HH:MM string
     */
    public static String formatMinutes(int minutes) {
        int hours = (minutes / 60) % 24;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    /**
     * Formats the interval as a readable time range (e.g., "08:30-08:50 (T001)").
     *
     * @return formatted string
     */
    @Override
    public String toString() {
        String timeRange = formatMinutes(startMinutes) + "-" + formatMinutes(endMinutes);
        if (trainId != null && !trainId.isEmpty()) {
            return timeRange + " (" + trainId + ")";
        }
        return timeRange;
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
