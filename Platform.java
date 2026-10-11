import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A physical railway platform with a fixed length and sorted occupancy intervals.
 */
public class Platform {
    private static final Comparator<Interval> BY_START =
            Comparator.comparingInt(Interval::getStartMinutes);

    private final String id;
    private final int length;
    private final List<Interval> occupiedIntervals = new ArrayList<>();

    public Platform(String id, int length) {
        this.id = Objects.requireNonNull(id, "platform id must not be null");
        if (length <= 0) {
            throw new IllegalArgumentException("platform length must be positive: " + length);
        }
        this.length = length;
    }

    public boolean isCompatible(int trainLength, int arrivalMinutes, int departureMinutes, int bufferMinutes) {
        if (trainLength <= 0 || trainLength > length || arrivalMinutes < 0
                || departureMinutes < arrivalMinutes || bufferMinutes < 0) {
            return false;
        }
        Interval candidate = new Interval(arrivalMinutes, departureMinutes + bufferMinutes, null);
        for (Interval occupied : occupiedIntervals) {
            if (candidate.overlaps(occupied)) {
                return false;
            }
        }
        return true;
    }

    public void occupy(Interval interval) {
        Objects.requireNonNull(interval, "interval must not be null");
        int index = Collections.binarySearch(occupiedIntervals, interval, BY_START);
        if (index < 0) {
            index = -(index + 1);
        }
        occupiedIntervals.add(index, interval);
    }

    public void occupy(String trainId, int startMinutes, int endWithBufferMinutes) {
        occupy(new Interval(startMinutes, endWithBufferMinutes, trainId));
    }

    public void release(String trainId) {
        if (trainId != null) {
            occupiedIntervals.removeIf(interval -> trainId.equals(interval.getTrainId()));
        }
    }

    public boolean isOccupiedBy(String trainId) {
        if (trainId == null) {
            return false;
        }
        for (Interval interval : occupiedIntervals) {
            if (trainId.equals(interval.getTrainId())) {
                return true;
            }
        }
        return false;
    }

    public int getNextReleaseTime() {
        int earliest = Integer.MAX_VALUE;
        for (Interval interval : occupiedIntervals) {
            earliest = Math.min(earliest, interval.getEndMinutes());
        }
        return earliest;
    }

    public String getId() {
        return id;
    }

    public int getLength() {
        return length;
    }

    public List<Interval> getOccupiedIntervals() {
        return Collections.unmodifiableList(occupiedIntervals);
    }

    @Override
    public String toString() {
        return "Platform{" + "id='" + id + '\'' + ", length=" + length + "m"
                + ", occupiedIntervals=" + occupiedIntervals + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Platform)) return false;
        Platform platform = (Platform) other;
        return length == platform.length && id.equals(platform.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, length);
    }
}
