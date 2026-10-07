import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Payanam Junction - Platform Allocation Subsystem
 *
 * PlatformAllocationEngine coordinates automated platform berthing, priority-based
 * waiting list queues, and dynamic track allocation across station platforms.
 *
 * Key Architectural Decisions & Algorithmic Design:
 *
 * 1. Best-Fit Platform Selection (Shortest Compatible Platform):
 *    When allocating among multiple compatible platforms, the engine greedily selects
 *    the shortest platform that meets the physical length requirement. This prevents
 *    wasting long 500m tracks on short 250m trains, preserving capacity for larger rakes.
 *
 * 2. On-Demand Release-Time Min-Heap (Rebuilding Fresh vs. Incremental Heap):
 *    WHY we rebuild the release-time heap fresh on each query:
 *    Platform objects are stateful: trains arrive, get delayed, or depart, continuously
 *    mutating their internal occupied intervals and thus changing their getNextReleaseTime().
 *    Standard Java PriorityQueue does NOT support decrease-key or dynamic element mutation
 *    without violating the internal binary heap invariant. Maintaining a persistent heap
 *    would risk silent corruption or require complex external listeners. Since the station
 *    operates exactly 3 physical platforms, rebuilding a 3-element min-heap on demand takes
 *    O(P) time (where P = 3, virtually O(1)), ensuring 100% correctness with zero overhead.
 *
 * 3. PriorityQueue Re-Ranking (O(n) Removal Trade-Off):
 *    When schedule delays or priority shifts occur for a waiting train, reRank() removes
 *    the request via waitingTrains.remove(request) and re-inserts it via offer(). While
 *    remove(Object) executes an O(n) linear scan in a binary heap, this is a deliberately
 *    accepted and optimal trade-off given the station's realistic queue scale (n < 50 trains),
 *    avoiding the memory and code complexity of an indexed priority queue or Fibonacci heap.
 */
public class PlatformAllocationEngine {

    /** Comparator for ordering platforms by earliest release time. */
    private static final Comparator<Platform> RELEASE_TIME_COMPARATOR =
            Comparator.comparingInt(Platform::getNextReleaseTime);

    /** Registered station platforms managed by this engine. */
    private final List<Platform> platforms;

    /**
     * PriorityQueue maintaining waiting trains that could not be immediately berthed.
     * Ordered deterministically by PlatformRequest.COMPARATOR (Priority -> Arrival -> RequestTime).
     */
    private final PriorityQueue<PlatformRequest> waitingTrains;

    /**
     * Constructs a new PlatformAllocationEngine.
     *
     * @param platforms list of station platforms to manage; must not be null or empty
     * @throws NullPointerException     if platforms is null
     * @throws IllegalArgumentException if platforms list is empty
     */
    public PlatformAllocationEngine(List<Platform> platforms) {
        Objects.requireNonNull(platforms, "Platforms list cannot be null");
        if (platforms.isEmpty()) {
            throw new IllegalArgumentException("Engine requires at least one platform");
        }
        this.platforms = new ArrayList<>(platforms);
        this.waitingTrains = new PriorityQueue<>(PlatformRequest.COMPARATOR);
    }

    /**
     * Attempts to allocate a platform for the given train request.
     *
     * Logic:
     * 1. Filters all platforms where isCompatible() returns true.
     * 2. Among compatible platforms, chooses the SHORTEST platform that fits (best-fit).
     * 3. If a platform is found: occupies the platform with an Interval including buffer,
     *    and returns the assigned Platform.
     * 4. If none found: automatically places request into waitingTrains and returns null.
     *
     * @param request          the PlatformRequest being processed
     * @param trainLength      length of the train in meters
     * @param arrivalMinutes   train arrival time in minutes from midnight
     * @param departureMinutes train departure time in minutes from midnight
     * @param bufferMinutes    clearance buffer time in minutes required after departure
     * @return the allocated Platform, or null if added to waiting queue
     */
    public Platform tryAllocate(PlatformRequest request, int trainLength, int arrivalMinutes,
                                int departureMinutes, int bufferMinutes) {
        Objects.requireNonNull(request, "PlatformRequest cannot be null");

        List<Platform> compatiblePlatforms = new ArrayList<>();
        for (Platform platform : platforms) {
            if (platform.isCompatible(trainLength, arrivalMinutes, departureMinutes, bufferMinutes)) {
                compatiblePlatforms.add(platform);
            }
        }

        if (!compatiblePlatforms.isEmpty()) {
            // Best-fit selection: Sort by length ascending and pick the shortest compatible platform
            compatiblePlatforms.sort(Comparator.comparingInt(Platform::getLength));
            Platform bestFitPlatform = compatiblePlatforms.get(0);

            // Calculate interval boundaries including clearance buffer
            int endWithBuffer = departureMinutes + bufferMinutes;
            Interval occupiedInterval = new Interval(arrivalMinutes, endWithBuffer, request.getTrain().getTrainId());
            bestFitPlatform.occupy(occupiedInterval);

            return bestFitPlatform;
        }

        // No platform currently available; enqueue in priority waiting queue
        waitingTrains.offer(request);
        return null;
    }

    /**
     * Retries allocation for the highest-priority train in the waiting queue.
     *
     * Called whenever a platform is freed (e.g., train departure) to check if the
     * top waiting candidate can now be placed.
     *
     * Logic:
     * 1. Peeks at the head of waitingTrains without removing it.
     * 2. Tests compatibility against all platforms using the train's own schedule and length.
     * 3. If compatible: polls the request off waitingTrains, occupies the best-fit platform,
     *    and returns the assigned Platform.
     * 4. If still incompatible: leaves the request at the head of the queue and returns null.
     *
     * @param bufferMinutes clearance buffer time in minutes
     * @return newly assigned Platform, or null if no waiting train can be placed
     */
    public Platform retryNextWaiting(int bufferMinutes) {
        if (waitingTrains.isEmpty()) {
            return null;
        }

        PlatformRequest candidate = waitingTrains.peek();
        TrainService train = candidate.getTrain();

        int arrivalMinutes = candidate.getArrivalMinutes();
        int departureMinutes = candidate.getDepartureMinutes();
        int trainLength = train.getTrainLength();

        List<Platform> compatiblePlatforms = new ArrayList<>();
        for (Platform platform : platforms) {
            if (platform.isCompatible(trainLength, arrivalMinutes, departureMinutes, bufferMinutes)) {
                compatiblePlatforms.add(platform);
            }
        }

        if (!compatiblePlatforms.isEmpty()) {
            // Candidate fits! Remove permanently from queue
            waitingTrains.poll();

            // Select shortest compatible platform
            compatiblePlatforms.sort(Comparator.comparingInt(Platform::getLength));
            Platform selectedPlatform = compatiblePlatforms.get(0);

            int endWithBuffer = departureMinutes + bufferMinutes;
            selectedPlatform.occupy(new Interval(arrivalMinutes, endWithBuffer, train.getTrainId()));

            return selectedPlatform;
        }

        // Does not fit anywhere yet; keep at head of waiting queue
        return null;
    }

    /**
     * Re-ranks an existing waiting request whose schedule or ranking inputs have changed.
     *
     * Because Java's PriorityQueue lacks a native decrease-key / key-update operation,
     * this removes the request via an O(n) scan and re-offers it to restore heap ordering.
     *
     * @param request the PlatformRequest to re-rank
     */
    public void reRank(PlatformRequest request) {
        if (request == null) {
            return;
        }
        // Remove existing instance (O(n) linear search trade-off, optimal for station scale)
        boolean removed = waitingTrains.remove(request);
        if (removed) {
            // Re-offer to re-heapify according to updated attributes
            waitingTrains.offer(request);
        }
    }

    /**
     * Returns a read-only snapshot of the waiting queue in true priority order.
     *
     * Note: PriorityQueue.iterator() does NOT traverse in sorted heap order.
     * We drain or collect and explicitly sort with PlatformRequest.COMPARATOR
     * to ensure deterministic order for UI and log displays.
     *
     * @return list of waiting requests sorted by priority
     */
    public List<PlatformRequest> getWaitingSnapshot() {
        List<PlatformRequest> snapshot = new ArrayList<>(waitingTrains);
        snapshot.sort(PlatformRequest.COMPARATOR);
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * Answers "which platform frees up next" by freshly constructing a min-heap
     * of platforms ordered by getNextReleaseTime().
     *
     * Rebuilding fresh each time guarantees zero stale key issues when platforms
     * undergo interval changes.
     *
     * @return the platform that will become available next, or null if no platforms exist
     */
    public Platform getNextFreePlatform() {
        if (platforms.isEmpty()) {
            return null;
        }
        PriorityQueue<Platform> releaseHeap = new PriorityQueue<>(RELEASE_TIME_COMPARATOR);
        releaseHeap.addAll(platforms);
        return releaseHeap.peek();
    }

    /**
     * Returns the number of trains currently waiting for platform allocation.
     *
     * @return waiting queue count
     */
    public int getWaitingCount() {
        return waitingTrains.size();
    }

    /**
     * Returns the list of managed platforms.
     *
     * @return unmodifiable list of platforms
     */
    public List<Platform> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }
}
