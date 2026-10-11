import java.util.Objects;

/**
 * A half-open platform occupancy interval measured in minutes from midnight.
 */
public class Interval {
    private final int startMinutes;
    private final int endMinutes;
    private final String trainId;

    public Interval(int startMinutes, int endMinutes, String trainId) {
        if (startMinutes < 0) {
            throw new IllegalArgumentException("startMinutes must be non-negative: " + startMinutes);
        }
        if (endMinutes < startMinutes) {
            throw new IllegalArgumentException("endMinutes cannot be before startMinutes");
        }
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.trainId = trainId;
    }

    public boolean overlaps(Interval other) {
        Objects.requireNonNull(other, "other interval must not be null");
        return startMinutes < other.endMinutes && other.startMinutes < endMinutes;
    }

    public int getStartMinutes() {
        return startMinutes;
    }

    public int getEndMinutes() {
        return endMinutes;
    }

    public String getTrainId() {
        return trainId;
    }

    public static String formatMinutes(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    @Override
    public String toString() {
        String range = formatMinutes(startMinutes) + "-" + formatMinutes(endMinutes);
        return trainId == null || trainId.isEmpty() ? range : range + " (" + trainId + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Interval)) return false;
        Interval interval = (Interval) other;
        return startMinutes == interval.startMinutes
                && endMinutes == interval.endMinutes
                && Objects.equals(trainId, interval.trainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startMinutes, endMinutes, trainId);
    }
}
