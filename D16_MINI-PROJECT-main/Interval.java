import java.util.Objects;

/**
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
     * Returns the start time of the interval in minutes from midnight.
     *
     * @return startMinutes
     */
    public int getStartMinutes() {
        return startMinutes;
    }

    /**
     * Returns the end time of the interval in minutes from midnight.
     *
     * @return endMinutes
     */
    public int getEndMinutes() {
        return endMinutes;
    }

    /**
     * Returns the identifier of the occupying train.
     *
     * @return trainId (or null if temporary candidate)
     */
    public String getTrainId() {
        return trainId;
    }

    /**
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
