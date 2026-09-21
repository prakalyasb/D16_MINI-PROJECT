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
        const trainLabel = candidateTrainId ? `Train ${candidateTrainId}` : "Train";
        return {
            ok: false,
            reason: `Platform ${pfLabel} too short for ${trainLabel} (needs ${trainLength}m, platform is ${platform.length}m)`
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
                const trainLabel = candidateTrainId ? `Train ${candidateTrainId}` : "Train";
                return {
                    ok: false,
                    reason: `Platform ${pfLabel} already occupied ${inv.arrival}–${inv.departure} — ${trainLabel} (${arrivalTime}–${departureTime}) overlaps.`
                };
            }
        }
    }

    return { ok: true };
}

// Attach to window so it is accessible globally
window.parseTimeToMinutes = parseTimeToMinutes;
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
     * Cancels an existing reservation and promotes the next waiting passenger if present.
     * Reassigns the EXACT freed seat number to the front waiting passenger.
     *
     * @param {string} reservationId
     * @param {RailwaySystem} systemRef
     * @returns {Reservation|null} promoted Reservation or null
     */
    cancelBooking(reservationId, systemRef) {
        this.lastCancelResult = null;

        // 1. Scan this.seats for the matching reservation ID
        let seatIndex = this.seats.findIndex(r => r && r.id === reservationId);

        if (seatIndex === -1) {
            console.error(`Reservation ${reservationId} not found on Train ${this.id}`);
            const err = `Reservation ${reservationId} not found on Train ${this.id}`;
            window.eventLog.push(`CANCEL ERROR: ${err}`);
            this.lastCancelResult = { success: false, error: err, freedSeatNumber: null, promotedReservation: null };
            if (systemRef) {
                systemRef.lastCancelResult = this.lastCancelResult;
                systemRef.saveToStorage();
            }
            return { ok: false, reason: "Reservation not found" };
        }

        const cancelledRes = this.seats[seatIndex];
        if (cancelledRes.status === "CANCELLED") {
            console.error(`Reservation ${reservationId} is already cancelled`);
            const err = `Reservation ${reservationId} is already cancelled`;
            window.eventLog.push(`CANCEL ERROR: ${err}`);
            this.lastCancelResult = { success: false, error: err, freedSeatNumber: null, promotedReservation: null };
            if (systemRef) {
                systemRef.lastCancelResult = this.lastCancelResult;
                systemRef.saveToStorage();
            }
            return { ok: false, reason: "Reservation is already cancelled" };
        }

        // 2. Mark it CANCELLED and clear this seat slot
        cancelledRes.markCancelled();
        const freedSeat = seatIndex + 1;
        this.seats[seatIndex] = null;

        const cancelLog = `CANCEL: Reservation ${reservationId} on Train ${this.id} cancelled (Seat ${freedSeat} freed)`;
        window.eventLog.push(cancelLog);

        // 3. If waitingQueue.length > 0, shift() front passenger and assign SAME seat slot
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

            return promotedRes;
        }

        // 4. If waitingQueue is empty, return null (seat stays empty, no promotion)
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

        return null;
    }
}

/**
 * Platform Class
 * Models station platforms with physical length limits and occupied intervals
 */
class Platform {
    constructor(id, length) {
        this.id = id;
        this.length = length; // in meters
        this.occupiedIntervals = []; // List of { trainId, arrival, departure, startMin, endMin }
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
        this.loadFromStorage();
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
     * Cancels a booking and handles waitlist promotion
     */
    cancelBooking(trainId, reservationId) {
        const train = this.trains.get(trainId);
        if (!train) {
            console.error(`Train ${trainId} not found`);
            this.lastCancelResult = {
                success: false,
                error: `Train ${trainId} not found`,
                freedSeatNumber: null,
                promotedReservation: null
            };
            return { ok: false, reason: `Train ${trainId} not found` };
        }

        const res = train.cancelBooking(reservationId, this);
        this.lastCancelResult = train.lastCancelResult;
        return res;
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

    /**
     * Persists current state to sessionStorage
     */
    saveToStorage() {
        try {
            const data = {
                idCounter: this.idCounter,
                passengerCounter: this.passengerCounter,
                eventLog: window.eventLog,
                trains: Array.from(this.trains.values()).map(t => ({
                    id: t.id,
                    name: t.name,
                    capacity: t.capacity,
                    arrival: t.arrival,
                    departure: t.departure,
                    length: t.length,
                    priority: t.priority,
                    seats: t.seats.map(r => r ? {
                        id: r.id,
                        passenger: r.passenger,
                        trainId: r.trainId,
                        seatNumber: r.seatNumber,
                        status: r.status,
                        bookingTime: r.bookingTime
                    } : null),
                    confirmedReservations: t.confirmedReservations,
                    waitingQueue: t.waitingQueue
                })),
                platforms: Array.from(this.platforms.values()).map(pf => ({
                    id: pf.id,
                    length: pf.length,
                    occupiedIntervals: pf.occupiedIntervals
                })),
                passengers: Array.from(this.passengers.values())
            };
            sessionStorage.setItem("payanam_railway_state", JSON.stringify(data));
        } catch (e) {
            // Fallback gracefully if storage is restricted
        }
    }

    /**
     * Restores state from sessionStorage if present
     */
    loadFromStorage() {
        try {
            const raw = sessionStorage.getItem("payanam_railway_state");
            if (!raw) return;

            const data = JSON.parse(raw);
            if (data.idCounter !== undefined) this.idCounter = data.idCounter;
            if (data.passengerCounter !== undefined) this.passengerCounter = data.passengerCounter;
            if (Array.isArray(data.eventLog) && data.eventLog.length > 0) {
                window.eventLog = data.eventLog;
            }

            if (Array.isArray(data.passengers)) {
                data.passengers.forEach(p => {
                    this.passengers.set(p.id, new Passenger(p.id, p.name));
                });
            }

            if (Array.isArray(data.trains)) {
                data.trains.forEach(tData => {
                    const train = this.trains.get(tData.id);
                    if (train) {
                        if (Array.isArray(tData.seats)) {
                            train.seats = tData.seats.map(r => {
                                if (!r) return null;
                                const res = new Reservation(r.id, r.passenger, r.trainId, r.seatNumber);
                                res.status = r.status;
                                res.bookingTime = r.bookingTime;
                                return res;
                            });
                        } else if (Array.isArray(tData.confirmedReservations)) {
                            train.confirmedReservations = tData.confirmedReservations.map(r => {
                                const res = new Reservation(r.id, r.passenger, r.trainId, r.seatNumber);
                                res.status = r.status;
                                res.bookingTime = r.bookingTime;
                                return res;
                            });
                        }

                        if (Array.isArray(tData.waitingQueue)) {
                            train.waitingQueue = tData.waitingQueue.map(p => new Passenger(p.id, p.name));
                        }
                    }
                });
            }

            if (Array.isArray(data.platforms)) {
                data.platforms.forEach(pfData => {
                    const pf = this.platforms.get(pfData.id);
                    if (pf && Array.isArray(pfData.occupiedIntervals)) {
                        pf.occupiedIntervals = pfData.occupiedIntervals;
                    }
                });
            }
        } catch (e) {
            // Ignore parse errors and retain initial state
        }
    }

    /**
     * Resets system to fresh default state
     */
    reset() {
        try {
            sessionStorage.removeItem("payanam_railway_state");
        } catch (e) {}
        this.trains.clear();
        this.passengers.clear();
        this.idCounter = 0;
        this.passengerCounter = 0;
        this.lastCancelResult = null;
        window.eventLog = [];
        this.initPlatforms();
        this.initTrains();
        this.saveToStorage();
    }
}

// Global shared singleton instance
if (!window.railwaySystem) {
    window.railwaySystem = new RailwaySystem();
}
