import java.util.Comparator;
import java.util.Objects;

/**
 * Base request used by the platform-allocation priority queue.
 */
public abstract class PlatformRequest {
    public static final Comparator<PlatformRequest> PRIORITY_COMPARATOR =
            Comparator.comparingInt(PlatformRequest::priorityRank)
                    .thenComparingInt(PlatformRequest::getScheduledArrivalMinutes)
                    .thenComparingLong(PlatformRequest::getRequestTime);
    public static final Comparator<PlatformRequest> COMPARATOR = PRIORITY_COMPARATOR;

    private final TrainService train;
    private final long requestTime;

    public PlatformRequest(TrainService train) {
        this.train = Objects.requireNonNull(train, "train must not be null");
        this.requestTime = System.currentTimeMillis();
    }

    public abstract int priorityRank();

    public int getScheduledArrivalMinutes() {
        return parseTimeToMinutes(train.getArrivalTime());
    }

    public int getArrivalMinutes() {
        return getScheduledArrivalMinutes();
    }

    public int getDepartureMinutes() {
        return parseTimeToMinutes(train.getDepartureTime());
    }

    public TrainService getTrain() {
        return train;
    }

    public String getTrainId() {
        return train.getTrainId();
    }

    public long getRequestTime() {
        return requestTime;
    }

    public static int parseTimeToMinutes(String time) {
        if (time == null || time.trim().isEmpty()) {
            return 0;
        }
        String value = time.trim().toUpperCase();
        boolean pm = value.endsWith("PM");
        boolean am = value.endsWith("AM");
        if (pm || am) {
            value = value.substring(0, value.length() - 2).trim();
        }
        String[] parts = value.split(":");
        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            if (minutes < 0 || minutes > 59 || hours < 0 || hours > 24) {
                return 0;
            }
            if (pm && hours < 12) hours += 12;
            if (am && hours == 12) hours = 0;
            return hours * 60 + minutes;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return String.format("%s [Train: %s, Priority: %d, Arr: %s, Dep: %s, ReqTime: %d]",
                getClass().getSimpleName(), train.getTrainId(), priorityRank(),
                train.getArrivalTime(), train.getDepartureTime(), requestTime);
    }
}
