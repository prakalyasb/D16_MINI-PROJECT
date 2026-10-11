/**
 * Payanam Junction - Railway Reservation System
 * sim.js: Shared simulation and logic layer mirroring Java backend classes.
 * Plain ES6 JavaScript - no frameworks, no external dependencies.
 */

// Global event log holding console-style output lines
if (!window.eventLog) {
    window.eventLog = [];
}

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

        // Confirmed reservations kept strictly sorted by bookingTime
        this.confirmedReservations = [];

        // Waiting list queue strictly used as FIFO (push to add, shift to remove)
        this.waitingQueue = [];
    }

    /**
     * Checks if all seats are booked
     */
    isFull() {
        return this.confirmedReservations.length >= this.capacity;
    }

    /**
     * Returns count of remaining free seats
     */
    getAvailableSeats() {
        return Math.max(0, this.capacity - this.confirmedReservations.length);
    }

    /**
     * Returns count of passengers in waiting list
     */
    getWaitingCount() {
        return this.waitingQueue.length;
    }

    /**
     * Finds the lowest available seat number (1 to capacity)
     */
    findNextAvailableSeatNumber() {
        const takenSeats = new Set(this.confirmedReservations.map(r => r.seatNumber));
        for (let s = 1; s <= this.capacity; s++) {
            if (!takenSeats.has(s)) {
                return s;
            }
        }
        return this.confirmedReservations.length + 1;
    }

    /**
     * Attempts to book a seat for the passenger.
     * Returns the Reservation if confirmed, or null if added to waitingQueue.
     *
     * @param {Passenger} passenger
     * @param {RailwaySystem} systemRef
     * @returns {Reservation|null}
     */
    bookSeat(passenger, systemRef) {
        if (!this.isFull()) {
            const reservationId = systemRef ? systemRef.generateReservationId() : `R${String(Date.now()).slice(-3)}`;
            const seatNumber = this.findNextAvailableSeatNumber();
            const reservation = new Reservation(reservationId, passenger, this.id, seatNumber);

            // Maintain sorted order by bookingTime
            this.confirmedReservations.push(reservation);
            this.confirmedReservations.sort((a, b) => a.bookingTime - b.bookingTime);

            // Log event in exact Java console style
            const logLine = `BOOK: Passenger ${passenger.id} (${passenger.name}) -> Train ${this.id}, seat ${seatNumber}/${this.capacity} CONFIRMED`;
            window.eventLog.push(logLine);
            if (systemRef) systemRef.saveToStorage();

            return reservation;
        } else {
            // Train full -> strictly FIFO enqueue
            this.waitingQueue.push(passenger);
            const position = this.waitingQueue.length;

            const logLine = `BOOK: Train ${this.id} full (${this.capacity}/${this.capacity}) -> Passenger ${passenger.id} (${passenger.name}) added to waiting list, position ${position}`;
            window.eventLog.push(logLine);
            if (systemRef) systemRef.saveToStorage();

            return null;
        }
    }

    /**
     * Cancels an existing reservation and promotes the next waiting passenger if present.
     * Returns promoted Reservation or null.
     *
     * @param {string} reservationId
     * @param {RailwaySystem} systemRef
     * @returns {Reservation|null}
     */
    cancelBooking(reservationId, systemRef) {
        const foundIndex = this.confirmedReservations.findIndex(r => r.id === reservationId);

        if (foundIndex === -1) {
            const logLine = `CANCEL ERROR: Reservation ${reservationId} not found on Train ${this.id}`;
            window.eventLog.push(logLine);
            if (systemRef) systemRef.saveToStorage();
            return null;
        }

        const cancelledRes = this.confirmedReservations[foundIndex];
        cancelledRes.markCancelled();
        const freedSeat = cancelledRes.seatNumber;

        // Remove from confirmed reservations
        this.confirmedReservations.splice(foundIndex, 1);

        const cancelLog = `CANCEL: Reservation ${reservationId} on Train ${this.id} cancelled`;
        window.eventLog.push(cancelLog);

        // If waiting passengers exist, strictly FIFO dequeue the first passenger
        if (this.waitingQueue.length > 0) {
            const promotedPassenger = this.waitingQueue.shift(); // STRICT FIFO
            const newResId = systemRef ? systemRef.generateReservationId() : `R${String(Date.now()).slice(-3)}`;
            const promotedRes = new Reservation(newResId, promotedPassenger, this.id, freedSeat);

            this.confirmedReservations.push(promotedRes);
            this.confirmedReservations.sort((a, b) => a.bookingTime - b.bookingTime);

            const promoteLog = `PROMOTE: Passenger ${promotedPassenger.id} (${promotedPassenger.name}) promoted from waiting list -> seat ${freedSeat} CONFIRMED as ${newResId}`;
            window.eventLog.push(promoteLog);
            if (systemRef) systemRef.saveToStorage();

            return promotedRes;
        }

        if (systemRef) systemRef.saveToStorage();
        return null;
    }
}

/**
 * Platform Class
 * Models station platforms with length constraints
 */
class Platform {
    constructor(id, length) {
        this.id = id;
        this.length = length; // in meters
        this.occupiedIntervals = []; // List of { trainId, start, end }
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
     * Looks up or registers a passenger, then delegates booking to TrainService
     */
    bookSeat(trainId, passengerId, passengerName) {
        const train = this.trains.get(trainId);
        if (!train) {
            window.eventLog.push(`ERROR: Train ${trainId} not found`);
            this.saveToStorage();
            return null;
        }

        let passenger = this.passengers.get(passengerId);
        if (!passenger) {
            passenger = new Passenger(passengerId || this.generatePassengerId(), passengerName || "Passenger");
            this.registerPassenger(passenger);
        }

        return train.bookSeat(passenger, this);
    }

    /**
     * Cancels a booking and handles waitlist promotion
     */
    cancelBooking(trainId, reservationId) {
        const train = this.trains.get(trainId);
        if (!train) {
            window.eventLog.push(`ERROR: Train ${trainId} not found`);
            this.saveToStorage();
            return null;
        }

        return train.cancelBooking(reservationId, this);
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
     * Persists current state and event log to sessionStorage
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
                    confirmedReservations: t.confirmedReservations,
                    waitingQueue: t.waitingQueue
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
                        train.confirmedReservations = tData.confirmedReservations.map(r => {
                            const res = new Reservation(r.id, r.passenger, r.trainId, r.seatNumber);
                            res.status = r.status;
                            res.bookingTime = r.bookingTime;
                            return res;
                        });
                        train.waitingQueue = tData.waitingQueue.map(p => new Passenger(p.id, p.name));
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
        window.eventLog = [];
        this.initPlatforms();
        this.initTrains();
        window.eventLog.push("[SYSTEM] Railway Reservation System reset to initial schedule.");
        this.saveToStorage();
    }
}

// Global shared singleton instance
if (!window.railwaySystem) {
    window.railwaySystem = new RailwaySystem();
}
