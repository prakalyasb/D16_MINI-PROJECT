/**
 * Payanam Junction - Railway Reservation System
 * sim.js: Shared simulation and logic layer mirroring Java backend classes.
 * Plain ES6 JavaScript - no frameworks, no external dependencies.
 */

// Global event log holding plain text output lines (kept for internal log use)
if (!window.eventLog) {
    window.eventLog = [];
}

/**
 * Utility: Converts HH:MM string into total minutes from midnight
 * e.g. "08:30" -> 510
 *
 * @param {string} timeStr - Time in HH:MM format
 * @returns {number} minutes from midnight
 */
function parseTimeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const parts = timeStr.trim().split(":");
    const h = parseInt(parts[0], 10) || 0;
    const m = parseInt(parts[1], 10) || 0;
    return h * 60 + m;
}

/**
 * Utility: Converts minutes from midnight into HH:MM string
 * e.g. 510 -> "08:30"
 *
 * @param {number} totalMinutes
 * @returns {string} HH:MM format
 */
function minutesToTime(totalMinutes) {
    if (isNaN(totalMinutes) || totalMinutes < 0) totalMinutes = 0;
    const h = Math.floor(totalMinutes / 60) % 24;
    const m = Math.floor(totalMinutes % 60);
    return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/**
 * Validates whether a platform can accommodate a train given its physical length
 * and time interval [arrivalTime, departureTime + bufferMinutes].
 *
 * Real overlap and length checking logic as required by Phase 2.
 *
 * @param {Platform} platform - The target Platform object
 * @param {string} arrivalTime - HH:MM arrival time
 * @param {string} departureTime - HH:MM departure time
 * @param {number} [bufferMinutes=5] - Clearance buffer after departure
 * @param {number} [trainLength=0] - Length of train in meters
 * @param {string} [candidateTrainId=""] - Optional train ID for detailed messages
 * @returns {{ ok: boolean, reason?: string }}
 */
function isPlatformFree(platform, arrivalTime, departureTime, bufferMinutes = 5, trainLength = 0, candidateTrainId = "") {
    if (!platform) {
        return { ok: false, reason: "Platform not specified" };
    }

    const pfLabel = platform.id.replace("PF-", "");

    // 1. Length Check
    if (trainLength > platform.length) {
        return {
            ok: false,
            reason: `Platform ${pfLabel} too short: needs ${trainLength}m, has ${platform.length}m`
        };
    }

    // 2. Interval Overlap Check
    const arrMin = parseTimeToMinutes(arrivalTime);
    const depMin = parseTimeToMinutes(departureTime) + (bufferMinutes || 0);

    if (Array.isArray(platform.occupiedIntervals)) {
        for (const inv of platform.occupiedIntervals) {
            // Ignore interval if it belongs to the same train being checked
            if (candidateTrainId && inv.trainId === candidateTrainId) {
                continue;
            }

            const invStart = inv.startMin !== undefined ? inv.startMin : parseTimeToMinutes(inv.arrival);
            const invEnd = inv.endMin !== undefined ? inv.endMin : parseTimeToMinutes(inv.departure) + (bufferMinutes || 0);

            // Two intervals [A, B] and [C, D] overlap if A < D and C < B
            if (arrMin < invEnd && invStart < depMin) {
                const trainLabel = inv.trainId ? `Train ${inv.trainId}` : "train";
                return {
                    ok: false,
                    reason: `Overlaps ${trainLabel}, ${inv.arrival}–${inv.departure}`
                };
            }
        }
    }

    return { ok: true };
}

// Attach to window so it is accessible globally
window.parseTimeToMinutes = parseTimeToMinutes;
window.minutesToTime = minutesToTime;
window.isPlatformFree = isPlatformFree;

/**
 * Passenger Class
 * Mirrors Passenger.java
 */
class Passenger {
    constructor(id, name) {
        this.id = id;
        this.name = name;
        this.requestTime = Date.now();
    }
}

/**
 * Reservation Class
 * Mirrors Reservation.java
 */
class Reservation {
    constructor(id, passenger, trainId, seatNumber) {
        this.id = id;
        this.passenger = passenger;
        this.trainId = trainId;
        this.seatNumber = seatNumber;
        this.status = "CONFIRMED";
        this.bookingTime = Date.now();
    }

    markCancelled() {
        this.status = "CANCELLED";
    }
}

/**
 * TrainService Class
 * Mirrors TrainService.java
 */
class TrainService {
    constructor(id, name, capacity = 10, arrival = "08:00", departure = "08:20", length = 350, priority = "SCHEDULED") {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.arrival = arrival;
        this.departure = departure;
        this.length = length;
        this.priority = priority; // "EMERGENCY", "CONNECTING", "SCHEDULED"

        // Fixed-size array where index 0 = seat 1, index 1 = seat 2, etc. Source of truth.
        this.seats = new Array(this.capacity).fill(null);

        // Waiting list queue strictly used as FIFO (push to add, shift to remove)
        this.waitingQueue = [];

        // Metadata on last cancellation
        this.lastCancelResult = null;
    }

    /**
     * Derived getter returning non-null reservations, sorted by bookingTime
     */
    get confirmedReservations() {
        return this.seats
            .filter(r => r !== null)
            .sort((a, b) => a.bookingTime - b.bookingTime);
    }

    /**
     * Setter for compatibility with legacy state restoration
     */
    set confirmedReservations(resList) {
        this.seats = new Array(this.capacity).fill(null);
        if (Array.isArray(resList)) {
            resList.forEach(r => {
                if (r && r.seatNumber >= 1 && r.seatNumber <= this.capacity) {
                    this.seats[r.seatNumber - 1] = r;
                }
            });
        }
    }

    /**
     * Checks if all seats are booked
     */
    isFull() {
        return this.seats.every(s => s !== null);
    }

    /**
     * Returns count of remaining free seats
     */
    getAvailableSeats() {
        return this.seats.filter(s => s === null).length;
    }

    /**
     * Returns count of passengers in waiting list
     */
    getWaitingCount() {
        return this.waitingQueue.length;
    }

    /**
     * Finds lowest available seat number (1 to capacity) or -1 if full
     */
    findNextAvailableSeatNumber() {
        for (let i = 0; i < this.capacity; i++) {
            if (this.seats[i] === null) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Attempts to book a specific seat for the passenger.
     * seatNumber is 1-indexed (1 to capacity) as chosen by the user.
     *
     * @param {Passenger} passenger
     * @param {number} seatNumber - 1-indexed seat number
     * @param {RailwaySystem} systemRef
     * @returns {{ ok: boolean, reason?: string, reservation?: Reservation }}
     */
    bookSeat(passenger, seatNumber, systemRef) {
        const sNum = parseInt(seatNumber, 10);
        if (isNaN(sNum) || sNum < 1 || sNum > this.capacity) {
            return { ok: false, reason: "Invalid seat number" };
        }

        // If seat is already occupied, return clear error without overwriting
        if (this.seats[sNum - 1] !== null) {
            return { ok: false, reason: "Seat already booked" };
        }

        // Allocate the requested seat
        const reservationId = systemRef ? systemRef.generateReservationId() : `R${String(Date.now()).slice(-3)}`;
        const reservation = new Reservation(reservationId, passenger, this.id, sNum);
        this.seats[sNum - 1] = reservation;

        const logLine = `BOOK: Passenger ${passenger.id} (${passenger.name}) -> Train ${this.id}, seat ${sNum}/${this.capacity} CONFIRMED (${reservationId})`;
        window.eventLog.push(logLine);
        if (systemRef) systemRef.saveToStorage();

        return { ok: true, reservation };
    }

    /**
     * Explicitly joins the FIFO waiting list when the train is full
     *
     * @param {Passenger} passenger
     * @param {RailwaySystem} systemRef
     * @returns {{ ok: boolean, reason?: string, position?: number }}
     */
    joinWaitingList(passenger, systemRef) {
        if (!this.isFull()) {
            return { ok: false, reason: "Seats are still available for booking" };
        }

        this.waitingQueue.push(passenger);
        const position = this.waitingQueue.length;

        const logLine = `WAITLIST: Train ${this.id} full (${this.capacity}/${this.capacity}) -> Passenger ${passenger.id} (${passenger.name}) added to waiting list, position ${position}`;
        window.eventLog.push(logLine);
        if (systemRef) systemRef.saveToStorage();

        return { ok: true, position };
    }

    /**
     * Cancels an existing booking by Passenger ID and promotes the next waiting passenger if present.
     *
     * @param {string} passengerId
     * @param {RailwaySystem} [systemRef]
     * @returns {{ ok: boolean, reason?: string, cancelledSeat?: number, promoted?: Reservation|null }}
     */
    cancelBookingByPassengerId(passengerId, systemRef) {
        this.lastCancelResult = null;

        if (!passengerId) {
            return { ok: false, reason: "Passenger ID is required" };
        }

        const targetPId = String(passengerId).trim();

        // Search this.seats for the entry where seat.passenger.id === passengerId
        let seatIndex = this.seats.findIndex(r => r && r.passenger && (r.passenger.id === targetPId || r.passenger.id.toUpperCase() === targetPId.toUpperCase()));

        if (seatIndex === -1) {
            const reason = "No confirmed booking found for passenger " + passengerId;
            console.error(reason + " on Train " + this.id);
            window.eventLog.push(`CANCEL ERROR: ${reason} on Train ${this.id}`);
            this.lastCancelResult = { success: false, error: reason, freedSeatNumber: null, promotedReservation: null };
            if (systemRef) {
                systemRef.lastCancelResult = this.lastCancelResult;
                systemRef.saveToStorage();
            }
            return { ok: false, reason: reason };
        }

        const cancelledRes = this.seats[seatIndex];
        cancelledRes.markCancelled();
        const freedSeat = seatIndex + 1;
        this.seats[seatIndex] = null;

        const cancelLog = `CANCEL: Passenger ${cancelledRes.passenger.id} (${cancelledRes.passenger.name}) on Train ${this.id} cancelled (Seat ${freedSeat} freed, reservation ${cancelledRes.id})`;
        window.eventLog.push(cancelLog);

        if (this.waitingQueue.length > 0) {
            const promotedPassenger = this.waitingQueue.shift(); // strictly FIFO shift()
            const newResId = systemRef ? systemRef.generateReservationId() : `R${String(Date.now()).slice(-3)}`;
            const promotedRes = new Reservation(newResId, promotedPassenger, this.id, freedSeat);

            this.seats[seatIndex] = promotedRes;

            const promoteLog = `PROMOTE: Passenger ${promotedPassenger.id} (${promotedPassenger.name}) promoted from waiting list -> seat ${freedSeat} CONFIRMED as ${newResId}`;
            window.eventLog.push(promoteLog);

            this.lastCancelResult = {
                success: true,
                error: null,
                freedSeatNumber: freedSeat,
                promotedReservation: promotedRes
            };

            if (systemRef) {
                systemRef.lastCancelResult = this.lastCancelResult;
                systemRef.saveToStorage();
            }

            return { ok: true, cancelledSeat: freedSeat, promoted: promotedRes };
        }

        this.lastCancelResult = {
            success: true,
            error: null,
            freedSeatNumber: freedSeat,
            promotedReservation: null
        };

        if (systemRef) {
            systemRef.lastCancelResult = this.lastCancelResult;
            systemRef.saveToStorage();
        }

        return { ok: true, cancelledSeat: freedSeat, promoted: null };
    }
}

class Interval {
    constructor(startMinutes, endMinutes, trainId) {
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.trainId = trainId;
        this.startMin = startMinutes;
        this.endMin = endMinutes;
        this.arrival = minutesToTime(startMinutes);
        this.departure = minutesToTime(endMinutes);
    }

    overlaps(other) {
        return this.startMinutes < other.endMinutes && other.startMinutes < this.endMinutes;
    }
}

class Platform {
    constructor(id, length) {
        this.id = id;
        this.length = length;
        this.occupiedIntervals = [];
    }

    isCompatible(trainLength, arrivalMinutes, departureMinutes, bufferMinutes = 10, trainId = "") {
        if (trainLength > this.length) {
            return {
                ok: false,
                reason: `Platform ${this.id.replace("PF-", "")} too short: needs ${trainLength}m, has ${this.length}m`
            };
        }
        const candidate = new Interval(arrivalMinutes, departureMinutes + bufferMinutes, trainId);
        const conflict = this.occupiedIntervals.find(interval =>
            interval.trainId !== trainId && candidate.overlaps(interval)
        );
        if (conflict) {
            return {
                ok: false,
                reason: `Overlaps ${conflict.trainId} ${minutesToTime(conflict.startMin)}-${minutesToTime(conflict.endMin)}`
            };
        }
        return { ok: true, reason: "" };
    }

    occupy(interval) {
        this.release(interval.trainId);
        this.occupiedIntervals.push({
            trainId: interval.trainId,
            arrival: minutesToTime(interval.startMinutes),
            departure: minutesToTime(interval.endMinutes),
            startMin: interval.startMinutes,
            endMin: interval.endMinutes,
            startMinutes: interval.startMinutes,
            endMinutes: interval.endMinutes
        });
        this.occupiedIntervals.sort((a, b) => a.startMin - b.startMin || a.trainId.localeCompare(b.trainId));
    }

    release(trainId) {
        const previousLength = this.occupiedIntervals.length;
        this.occupiedIntervals = this.occupiedIntervals.filter(interval => interval.trainId !== trainId);
        return this.occupiedIntervals.length !== previousLength;
    }

    getNextReleaseTime() {
        return this.occupiedIntervals.length
            ? Math.min(...this.occupiedIntervals.map(interval => interval.endMin))
            : Number.POSITIVE_INFINITY;
    }
}

/**
 * RailwaySystem Class
 * Central application and service layer coordination
 */
class RailwaySystem {
    constructor() {
        this.trains = new Map();
        this.platforms = new Map();
        this.passengers = new Map();
        this.idCounter = 0;
        this.passengerCounter = 0;
        this.lastCancelResult = null;

        this.initPlatforms();
        this.initTrains();
        this.allocationEngine = new PlatformAllocationEngine(Array.from(this.platforms.values()), this);
        this.allocationEngine.resetPhase2();
    }

    /**
     * Initializes 3 station platforms with lengths 300m, 400m, 500m
     */
    initPlatforms() {
        this.platforms.set("PF-1", new Platform("PF-1", 300));
        this.platforms.set("PF-2", new Platform("PF-2", 400));
        this.platforms.set("PF-3", new Platform("PF-3", 500));
    }

    /**
     * Initializes 6 pre-seeded TrainService objects
     */
    initTrains() {
        // Exactly 6 trains: 1 EMERGENCY, 1 CONNECTING, 4 SCHEDULED
        const trainList = [
            new TrainService("T001", "Vaigai Superfast", 10, "06:00", "06:20", 350, "SCHEDULED"),
            new TrainService("T002", "Pandian Express", 10, "08:15", "08:35", 420, "SCHEDULED"),
            new TrainService("T003", "Cheran Express", 10, "10:30", "10:50", 280, "SCHEDULED"),
            new TrainService("T004", "Vande Bharat Express", 10, "13:00", "13:15", 320, "SCHEDULED"),
            new TrainService("T005", "Medical Relief Special", 10, "15:45", "16:00", 250, "EMERGENCY"),
            new TrainService("T006", "Guruvayur Link Express", 10, "18:20", "18:40", 480, "CONNECTING")
        ];

        trainList.forEach(t => this.trains.set(t.id, t));
    }

    /**
     * Generates sequential zero-padded reservation IDs: R001, R002, etc.
     */
    generateReservationId() {
        this.idCounter++;
        return `R${String(this.idCounter).padStart(3, "0")}`;
    }

    /**
     * Generates sequential passenger IDs: P001, P002, etc.
     */
    generatePassengerId() {
        this.passengerCounter++;
        return `P${String(this.passengerCounter).padStart(3, "0")}`;
    }

    /**
     * Registers a passenger in the system
     */
    registerPassenger(passenger) {
        this.passengers.set(passenger.id, passenger);
        return passenger;
    }

    /**
     * Looks up or registers a passenger, then delegates seat booking to TrainService
     */
    bookSeat(trainId, passengerId, passengerName, seatNumber) {
        const train = this.trains.get(trainId);
        if (!train) {
            window.eventLog.push(`ERROR: Train ${trainId} not found`);
            this.saveToStorage();
            return { ok: false, reason: `Train ${trainId} not found` };
        }

        let passenger = this.passengers.get(passengerId);
        if (!passenger) {
            passenger = new Passenger(passengerId || this.generatePassengerId(), passengerName || "Passenger");
            this.registerPassenger(passenger);
        }

        return train.bookSeat(passenger, seatNumber, this);
    }

    /**
     * Explicitly registers a passenger on a full train's FIFO waiting list
     */
    joinWaitingList(trainId, passengerId, passengerName) {
        const train = this.trains.get(trainId);
        if (!train) {
            window.eventLog.push(`ERROR: Train ${trainId} not found`);
            this.saveToStorage();
            return { ok: false, reason: `Train ${trainId} not found` };
        }

        let passenger = this.passengers.get(passengerId);
        if (!passenger) {
            passenger = new Passenger(passengerId || this.generatePassengerId(), passengerName || "Passenger");
            this.registerPassenger(passenger);
        }

        return train.joinWaitingList(passenger, this);
    }

    /**
     * Cancels a booking by Passenger ID and handles waitlist promotion.
     * Supports both:
     *   cancelBookingByPassengerId(trainId, passengerId)
     *   cancelBookingByPassengerId(passengerId)  // global search across all trains
     *
     * @param {string} arg1 - trainId or passengerId
     * @param {string} [arg2] - passengerId if arg1 is trainId
     * @returns {{ ok: boolean, reason?: string, cancelledSeat?: number, promoted?: Reservation|null, trainId?: string, train?: TrainService }}
     */
    cancelBookingByPassengerId(arg1, arg2) {
        let trainId = null;
        let passengerId = null;

        if (arg2 !== undefined && arg2 !== null && String(arg2).trim() !== "") {
            trainId = String(arg1).trim();
            passengerId = String(arg2).trim();
        } else {
            passengerId = String(arg1).trim();
        }

        if (!passengerId) {
            const err = "Passenger ID is required";
            this.lastCancelResult = { success: false, error: err, freedSeatNumber: null, promotedReservation: null };
            return { ok: false, reason: err };
        }

        if (trainId) {
            const train = this.trains.get(trainId);
            if (!train) {
                const err = `Train ${trainId} not found`;
                console.error(err);
                this.lastCancelResult = { success: false, error: err, freedSeatNumber: null, promotedReservation: null };
                return { ok: false, reason: err };
            }
            const res = train.cancelBookingByPassengerId(passengerId, this);
            this.lastCancelResult = train.lastCancelResult;
            if (res && res.ok) {
                res.trainId = trainId;
                res.train = train;
            }
            return res;
        }

        // Global search across all trains
        for (const train of this.trains.values()) {
            const hasBooking = train.seats.some(r => r && r.passenger && (
                r.passenger.id === passengerId ||
                r.passenger.id.toUpperCase() === passengerId.toUpperCase()
            ));
            if (hasBooking) {
                const res = train.cancelBookingByPassengerId(passengerId, this);
                this.lastCancelResult = train.lastCancelResult;
                if (res && res.ok) {
                    res.trainId = train.id;
                    res.train = train;
                }
                return res;
            }
        }

        const notFound = `No confirmed booking found for passenger ${passengerId}`;
        console.error(notFound + " on any train");
        window.eventLog.push(`CANCEL ERROR: ${notFound} on any train`);
        this.lastCancelResult = { success: false, error: notFound, freedSeatNumber: null, promotedReservation: null };
        this.saveToStorage();
        return { ok: false, reason: notFound };
    }

    /**
     * Returns array of all trains
     */
    listAllTrains() {
        return Array.from(this.trains.values());
    }

    /**
     * Returns array of all platforms
     */
    listAllPlatforms() {
        return Array.from(this.platforms.values());
    }

    saveToStorage() {}

    loadFromStorage() {}

    /**
     * Resets system to fresh default state
     */
    reset() {
        this.trains.clear();
        this.passengers.clear();
        this.idCounter = 0;
        this.passengerCounter = 0;
        this.lastCancelResult = null;
        window.eventLog = [];
        this.initPlatforms();
        this.initTrains();
        if (this.allocationEngine) {
            this.allocationEngine.platforms = Array.from(this.platforms.values()).sort((a, b) => a.length - b.length || a.id.localeCompare(b.id));
            this.allocationEngine.railwaySystem = this;
        } else {
            this.allocationEngine = new PlatformAllocationEngine(Array.from(this.platforms.values()), this);
        }
        this.allocationEngine.resetPhase2();
    }
}

class PlatformRequest {
    constructor(train, requestTime) {
        this.train = train;
        this.requestTime = requestTime;
        this.arrivalMinutes = parseTimeToMinutes(train.arrival);
        this.departureMinutes = parseTimeToMinutes(train.departure);
    }

    getTrain() {
        return this.train;
    }

    priorityRank() {
        return 2;
    }

    static compare(left, right) {
        return left.priorityRank() - right.priorityRank()
            || left.arrivalMinutes - right.arrivalMinutes
            || left.requestTime - right.requestTime
            || left.train.id.localeCompare(right.train.id);
    }
}

class EmergencyArrival extends PlatformRequest {
    priorityRank() {
        return 0;
    }
}

class ConnectingArrival extends PlatformRequest {
    priorityRank() {
        return 1;
    }
}

class ScheduledArrival extends PlatformRequest {
    priorityRank() {
        return 2;
    }
}

function createPlatformRequest(train, requestTime) {
    const RequestType = train.priority === "EMERGENCY"
        ? EmergencyArrival
        : train.priority === "CONNECTING"
            ? ConnectingArrival
            : ScheduledArrival;
    return new RequestType(train, requestTime);
}

class PriorityQueue {
    constructor(compare = PlatformRequest.compare) {
        this.heap = [];
        this.compare = compare;
    }

    get size() {
        return this.heap.length;
    }

    offer(value) {
        this.heap.push(value);
        this._siftUp(this.heap.length - 1);
    }

    peek() {
        return this.heap[0] || null;
    }

    poll() {
        if (!this.heap.length) return null;
        const first = this.heap[0];
        const last = this.heap.pop();
        if (this.heap.length) {
            this.heap[0] = last;
            this._siftDown(0);
        }
        return first;
    }

    remove(value) {
        const index = this.heap.indexOf(value);
        if (index < 0) return false;
        const last = this.heap.pop();
        if (index < this.heap.length) {
            this.heap[index] = last;
            if (index > 0 && this.compare(this.heap[index], this.heap[Math.floor((index - 1) / 2)]) < 0) {
                this._siftUp(index);
            } else {
                this._siftDown(index);
            }
        }
        return true;
    }

    toSortedArray() {
        return this.heap.slice().sort(this.compare);
    }

    _siftUp(index) {
        while (index > 0) {
            const parent = Math.floor((index - 1) / 2);
            if (this.compare(this.heap[parent], this.heap[index]) <= 0) break;
            [this.heap[parent], this.heap[index]] = [this.heap[index], this.heap[parent]];
            index = parent;
        }
    }

    _siftDown(index) {
        for (;;) {
            const left = index * 2 + 1;
            const right = left + 1;
            let smallest = index;
            if (left < this.heap.length && this.compare(this.heap[left], this.heap[smallest]) < 0) smallest = left;
            if (right < this.heap.length && this.compare(this.heap[right], this.heap[smallest]) < 0) smallest = right;
            if (smallest === index) return;
            [this.heap[index], this.heap[smallest]] = [this.heap[smallest], this.heap[index]];
            index = smallest;
        }
    }
}

class PlatformAllocationEngine {
    constructor(platforms, railwaySystem, bufferMinutes = 10) {
        this.platforms = platforms.slice().sort((a, b) => a.length - b.length || a.id.localeCompare(b.id));
        this.railwaySystem = railwaySystem;
        this.bufferMinutes = bufferMinutes;
        this.waitingQueue = new PriorityQueue();
        this.assignments = new Map();
        this.requestSequence = 0;
    }

    _record(result) {
        window.eventLog.push(result.message);
        return result;
    }

    _compatiblePlatforms(train, arrivalMinutes, departureMinutes, ignoreTrainId = train.id) {
        return this.platforms.filter(platform =>
            platform.isCompatible(train.length, arrivalMinutes, departureMinutes, this.bufferMinutes, ignoreTrainId).ok
        );
    }

    _occupy(request, platform, arrivalMinutes, departureMinutes) {
        const train = request.train;
        platform.occupy(new Interval(arrivalMinutes, departureMinutes + this.bufferMinutes, train.id));
        this.assignments.set(train.id, platform);
        train.platformState = "ASSIGNED";
        train.platformId = platform.id;
        return platform;
    }

    _queue(request) {
        if (!this.waitingQueue.heap.some(item => item.train.id === request.train.id)) {
            this.waitingQueue.offer(request);
        }
        request.train.platformState = "WAITING";
        request.train.platformId = null;
    }

    tryAllocate(request, trainLength, arrivalMinutes, departureMinutes, bufferMinutes = this.bufferMinutes) {
        if (!(request instanceof PlatformRequest)) {
            return this._record({ ok: false, message: "Invalid platform request." });
        }
        if (!Number.isInteger(trainLength) || !Number.isInteger(arrivalMinutes)
            || !Number.isInteger(departureMinutes) || departureMinutes <= arrivalMinutes) {
            return this._record({ ok: false, message: `Invalid schedule for ${request.train.id}.` });
        }
        this.bufferMinutes = bufferMinutes;
        const train = request.train;
        if (trainLength > Math.max(...this.platforms.map(platform => platform.length))) {
            train.platformState = "REJECTED";
            train.platformId = null;
            const result = { ok: false, rejected: true, message: `${train.id} rejected: train length ${trainLength}m exceeds the longest platform (${Math.max(...this.platforms.map(platform => platform.length))}m).` };
            return this._record(result);
        }
        if (this.assignments.has(train.id)) {
            const platform = this.assignments.get(train.id);
            return this._record({ ok: true, platform, message: `${train.id} already holds ${platform.id}.` });
        }
        const existingRequest = this.waitingQueue.heap.find(item => item.train.id === train.id);
        if (existingRequest) {
            this.waitingQueue.remove(existingRequest);
        }
        const compatible = this._compatiblePlatforms(train, arrivalMinutes, departureMinutes);
        if (compatible.length) {
            const platform = compatible[0];
            this._occupy(request, platform, arrivalMinutes, departureMinutes);
            return this._record({ ok: true, platform, message: `${train.id} assigned to ${platform.id} (${minutesToTime(arrivalMinutes)}-${minutesToTime(departureMinutes + this.bufferMinutes)} incl. 10m clearance).` });
        }
        this._queue(request);
        return this._record({ ok: false, queued: true, message: `${train.id} waitlisted: no compatible platform is free for ${minutesToTime(arrivalMinutes)}-${minutesToTime(departureMinutes + this.bufferMinutes)}.` });
    }

    requestPlatform(trainId) {
        const train = this.railwaySystem.trains.get(trainId);
        if (!train) return this._record({ ok: false, message: `Train ${trainId} not found.` });
        const request = createPlatformRequest(train, ++this.requestSequence);
        return this.tryAllocate(request, train.length, parseTimeToMinutes(train.arrival), parseTimeToMinutes(train.departure));
    }

    retryNextWaiting() {
        const promoted = [];
        for (;;) {
            const ordered = this.waitingQueue.toSortedArray();
            let candidate = null;
            let platform = null;
            for (const request of ordered) {
                const compatible = this._compatiblePlatforms(
                    request.train,
                    parseTimeToMinutes(request.train.arrival),
                    parseTimeToMinutes(request.train.departure)
                );
                if (compatible.length) {
                    candidate = request;
                    platform = compatible[0];
                    break;
                }
            }
            if (!candidate || !platform) break;
            this.waitingQueue.remove(candidate);
            this._occupy(candidate, platform, parseTimeToMinutes(candidate.train.arrival), parseTimeToMinutes(candidate.train.departure));
            promoted.push(candidate.train.id);
            this._record({ ok: true, message: `${candidate.train.id} promoted from platform queue to ${platform.id}.` });
        }
        return this._record({ ok: true, promoted, message: promoted.length ? `Promoted ${promoted.join(", ")} from the platform queue.` : "No waiting train can be allocated yet." });
    }

    reRank(request) {
        this.waitingQueue.remove(request);
        this.waitingQueue.offer(request);
        return this._record({ ok: true, message: `${request.train.id} re-ranked in the platform waiting queue.` });
    }

    departTrain(trainId) {
        const platform = this.assignments.get(trainId);
        if (!platform) return this._record({ ok: false, message: `${trainId} does not currently hold a platform.` });
        platform.release(trainId);
        this.assignments.delete(trainId);
        const train = this.railwaySystem.trains.get(trainId);
        if (train) {
            train.platformId = null;
            train.platformState = "DEPARTED";
        }
        const retry = this.retryNextWaiting();
        return this._record({ ok: true, promoted: retry.promoted, message: `${trainId} departed ${platform.id}. ${retry.message}` });
    }

    delayTrain(trainId, newArrival, newDeparture) {
        const train = this.railwaySystem.trains.get(trainId);
        if (!train) return this._record({ ok: false, message: `Train ${trainId} not found.` });
        const arrival = typeof newArrival === "number" ? newArrival : parseTimeToMinutes(newArrival);
        const departure = typeof newDeparture === "number" ? newDeparture : parseTimeToMinutes(newDeparture);
        if (!Number.isInteger(arrival) || !Number.isInteger(departure) || arrival < 0 || departure <= arrival) {
            return this._record({ ok: false, message: `Invalid delayed schedule for ${trainId}.` });
        }
        const oldPlatform = this.assignments.get(trainId) || null;
        const waitingRequest = this.waitingQueue.heap.find(request => request.train.id === trainId) || null;
        if (oldPlatform) {
            oldPlatform.release(trainId);
            this.assignments.delete(trainId);
        }
        if (waitingRequest) this.waitingQueue.remove(waitingRequest);
        train.arrival = minutesToTime(arrival);
        train.departure = minutesToTime(departure);
        const request = waitingRequest || createPlatformRequest(train, ++this.requestSequence);
        request.arrivalMinutes = arrival;
        request.departureMinutes = departure;
        if (train.length > Math.max(...this.platforms.map(platform => platform.length))) {
            train.platformState = "REJECTED";
            train.platformId = null;
            return this._record({ ok: false, rejected: true, message: `${trainId} rejected after delay: train length ${train.length}m exceeds all platforms.` });
        }
        if (oldPlatform && oldPlatform.isCompatible(train.length, arrival, departure, this.bufferMinutes, trainId).ok) {
            this._occupy(request, oldPlatform, arrival, departure);
            const retry = this.retryNextWaiting();
            return this._record({ ok: true, platform: oldPlatform, message: `${trainId} delayed to ${train.arrival}-${train.departure}; it keeps ${oldPlatform.id}. ${retry.message}` });
        }
        const compatible = this._compatiblePlatforms(train, arrival, departure);
        if (compatible.length) {
            const platform = compatible[0];
            this._occupy(request, platform, arrival, departure);
            const retry = this.retryNextWaiting();
            return this._record({ ok: true, platform, message: `${trainId} delayed to ${train.arrival}-${train.departure}; reallocated to ${platform.id}. ${retry.message}` });
        }
        this._queue(request);
        const retry = this.retryNextWaiting();
        if (this.assignments.has(trainId)) {
            const platform = this.assignments.get(trainId);
            return this._record({ ok: true, platform, message: `${trainId} delayed and assigned to ${platform.id} after queue retry.` });
        }
        return this._record({ ok: false, queued: true, message: `${trainId} delayed to ${train.arrival}-${train.departure}; queued for a platform. ${retry.message}` });
    }

    previewDelay(trainId, newArrival, newDeparture) {
        const train = this.railwaySystem.trains.get(trainId);
        if (!train) return this._record({ ok: false, message: `Train ${trainId} not found.` });
        const arrival = typeof newArrival === "number" ? newArrival : parseTimeToMinutes(newArrival);
        const departure = typeof newDeparture === "number" ? newDeparture : parseTimeToMinutes(newDeparture);
        if (!Number.isInteger(arrival) || !Number.isInteger(departure) || departure <= arrival) {
            return this._record({ ok: false, message: `Invalid delay preview for ${trainId}.` });
        }
        const current = this.assignments.get(trainId);
        const result = current && current.isCompatible(train.length, arrival, departure, this.bufferMinutes, trainId).ok
            ? { ok: true, state: "KEEPS", platformId: current.id, message: `${trainId} will keep ${current.id}.` }
            : this._compatiblePlatforms(train, arrival, departure).length
                ? { ok: true, state: "MOVES", platformId: this._compatiblePlatforms(train, arrival, departure)[0].id, message: `${trainId} will move to ${this._compatiblePlatforms(train, arrival, departure)[0].id}.` }
                : { ok: train.length <= Math.max(...this.platforms.map(platform => platform.length)), state: train.length > Math.max(...this.platforms.map(platform => platform.length)) ? "REJECTED" : "QUEUED", platformId: null, message: train.length > Math.max(...this.platforms.map(platform => platform.length)) ? `${trainId} will be rejected: too long for every platform.` : `${trainId} will be re-queued.` };
        return this._record(result);
    }

    previewPlacement(trainId, platformId, arrival, departure, withBuffer = true) {
        const train = this.railwaySystem.trains.get(trainId);
        const platform = this.platforms.find(item => item.id === platformId);
        if (!train || !platform) return this._record({ ok: false, message: "Select a valid train and platform." });
        const start = typeof arrival === "number" ? arrival : parseTimeToMinutes(arrival);
        const end = typeof departure === "number" ? departure : parseTimeToMinutes(departure);
        const result = platform.isCompatible(train.length, start, end, withBuffer ? this.bufferMinutes : 0, trainId);
        return this._record({ ...result, message: result.ok ? `${trainId} fits ${platformId} at ${minutesToTime(start)}-${minutesToTime(end)}${withBuffer ? " with clearance" : ""}.` : `${trainId} cannot use ${platformId}: ${result.reason}.` });
    }

    moveTrain(trainId, platformId, arrival, departure) {
        const train = this.railwaySystem.trains.get(trainId);
        const platform = this.platforms.find(item => item.id === platformId);
        if (!train || !platform) return this._record({ ok: false, message: "Select a valid train and platform." });
        const start = typeof arrival === "number" ? arrival : parseTimeToMinutes(arrival);
        const end = typeof departure === "number" ? departure : parseTimeToMinutes(departure);
        const valid = platform.isCompatible(train.length, start, end, this.bufferMinutes, trainId);
        if (!valid.ok) return this._record({ ok: false, message: `${trainId} placement rejected: ${valid.reason}.` });
        const previous = this.assignments.get(trainId);
        if (previous) previous.release(trainId);
        this.assignments.delete(trainId);
        const request = this.waitingQueue.heap.find(item => item.train.id === trainId);
        if (request) this.waitingQueue.remove(request);
        train.arrival = minutesToTime(start);
        train.departure = minutesToTime(end);
        this._occupy(request || createPlatformRequest(train, ++this.requestSequence), platform, start, end);
        const retry = this.retryNextWaiting();
        return this._record({ ok: true, platform, message: `${trainId} placed on ${platformId} at ${train.arrival}-${train.departure}. ${retry.message}` });
    }

    getStatus() {
        const waiting = this.waitingQueue.toSortedArray();
        const trains = Array.from(this.railwaySystem.trains.values()).map(train => ({
            id: train.id,
            name: train.name,
            length: train.length,
            priority: train.priority,
            arrival: train.arrival,
            departure: train.departure,
            platformId: this.assignments.get(train.id)?.id || null,
            state: train.platformState || "UNASSIGNED",
            queuePosition: waiting.findIndex(request => request.train.id === train.id) + 1
        }));
        const intervals = this.platforms.flatMap(platform => platform.occupiedIntervals);
        const occupiedMinutes = intervals.reduce((sum, interval) => sum + interval.endMin - interval.startMin, 0);
        return {
            platforms: this.platforms.map(platform => ({
                id: platform.id,
                length: platform.length,
                occupiedIntervals: platform.occupiedIntervals.map(interval => ({ ...interval }))
            })),
            waitingQueue: waiting.map((request, index) => ({
                trainId: request.train.id,
                priority: request.train.priority,
                arrival: request.train.arrival,
                departure: request.train.departure,
                position: index + 1
            })),
            trains,
            utilizationPercentage: Math.min(100, Math.round(occupiedMinutes / (this.platforms.length * 1440) * 100))
        };
    }

    resetPhase2() {
        this.waitingQueue = new PriorityQueue();
        this.assignments.clear();
        this.requestSequence = 0;
        this.platforms.forEach(platform => { platform.occupiedIntervals = []; });
        const schedules = {
            T001: ["08:30", "09:00", 280],
            T002: ["08:30", "09:00", 350],
            T003: ["08:30", "09:00", 450],
            T004: ["08:45", "09:05", 250],
            T005: ["08:50", "09:10", 280],
            T006: ["09:05", "09:25", 650]
        };
        for (const [trainId, [arrival, departure, length]] of Object.entries(schedules)) {
            const train = this.railwaySystem.trains.get(trainId);
            if (!train) continue;
            [train.arrival, train.departure, train.length] = [arrival, departure, length];
            train.platformId = null;
            train.platformState = "UNASSIGNED";
        }
        window.eventLog = [];
        ["T001", "T002", "T003", "T004", "T005", "T006"].forEach(trainId => {
            const train = this.railwaySystem.trains.get(trainId);
            if (train) this.requestPlatform(trainId);
        });
        return this._record({ ok: true, message: "Phase 2 state reset with overlapping schedules, priority queue, and overlong-train case." });
    }
}

// Global shared singleton instance
if (!window.railwaySystem) {
    window.railwaySystem = new RailwaySystem();
}

window.Interval = Interval;
window.Platform = Platform;
window.PlatformRequest = PlatformRequest;
window.EmergencyArrival = EmergencyArrival;
window.ConnectingArrival = ConnectingArrival;
window.ScheduledArrival = ScheduledArrival;
window.PriorityQueue = PriorityQueue;
window.PlatformAllocationEngine = PlatformAllocationEngine;
