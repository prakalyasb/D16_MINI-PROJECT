(function () {
    const system = window.railwaySystem;
    const results = document.getElementById("scenarioResults");

    function startScenario() {
        return system.allocationEngine.resetPhase2();
    }

    function report(title, steps, passed) {
        results.className = `scenario-result ${passed ? "pass" : "fail"}`;
        results.replaceChildren();
        const heading = document.createElement("strong");
        heading.textContent = `${passed ? "PASS" : "FAIL"} — ${title}`;
        results.append(heading);
        steps.forEach(step => {
            const item = document.createElement("div");
            item.className = "scenario-step";
            item.textContent = `${step.passed ? "✓" : "✗"} ${step.text}`;
            results.append(item);
        });
    }

    function hasNoPlatformOverlaps() {
        return system.allocationEngine.getStatus().platforms.every(platform => {
            const intervals = platform.occupiedIntervals;
            return intervals.every((interval, index) => intervals.slice(index + 1).every(other =>
                interval.startMin >= other.endMin || other.startMin >= interval.endMin
            ));
        });
    }

    const scenarios = {
        seat() {
            startScenario();
            const train = system.trains.get("T001");
            train.seats.fill(null);
            train.waitingQueue = [];
            for (let seat = 1; seat <= 10; seat++) {
                system.bookSeat("T001", `P${String(seat).padStart(3, "0")}`, `Passenger ${seat}`, seat);
            }
            system.joinWaitingList("T001", "P011", "Oldest waiter");
            system.joinWaitingList("T001", "P012", "Next waiter");
            const waitingCount = train.waitingQueue.length;
            const cancellation = system.cancelBookingByPassengerId("T001", "P004");
            const promoted = train.seats[3];
            const fifoPromotion = cancellation.ok && promoted.passenger.id === "P011"
                && promoted.seatNumber === 4;
            report("Cancelled seat is reassigned FIFO", [
                { passed: train.isFull(), text: "Filled all 10 seats." },
                { passed: waitingCount === 2, text: "Added two passengers to the FIFO waiting list." },
                { passed: fifoPromotion, text: `Cancelled P004; seat 4 now belongs to ${promoted ? promoted.passenger.id : "nobody"}.` }
            ], fifoPromotion);
        },
        overlap() {
            startScenario();
            const status = system.allocationEngine.getStatus();
            const waitingWindowsClash = status.waitingQueue.length >= 2
                && status.waitingQueue.some(waiter => status.platforms.some(platform =>
                    platform.occupiedIntervals.some(interval =>
                        interval.startMin < window.parseTimeToMinutes(waiter.departure) + 10
                        && window.parseTimeToMinutes(waiter.arrival) < interval.endMin
                    )
                ));
            const valid = hasNoPlatformOverlaps();
            report("Overlapping arrivals stay separated", [
                { passed: waitingWindowsClash, text: "Seeded arrivals compete for platforms during occupied windows." },
                { passed: valid, text: "No actual occupied intervals overlap on a platform." }
            ], waitingWindowsClash && valid);
        },
        long() {
            startScenario();
            const train = system.trains.get("T006");
            const explicit = train.platformState === "REJECTED"
                && window.eventLog.some(line => line.includes("T006 rejected") && line.includes("650m"));
            report("Overlong train is rejected", [
                { passed: train.length === 650, text: "T006 is longer than every platform." },
                { passed: explicit, text: "The engine records the rejection and does not queue the train." }
            ], explicit);
        },
        delay() {
            startScenario();
            const result = system.allocationEngine.delayTrain("T001", "08:30", "10:00");
            const state = system.allocationEngine.getStatus().trains.find(train => train.id === "T001");
            const valid = (state.state === "ASSIGNED" || state.state === "WAITING")
                && hasNoPlatformOverlaps();
            report("Delayed occupation remains valid", [
                { passed: result.ok || result.queued, text: result.message },
                { passed: valid, text: `T001 ends ${state.state.toLowerCase()} with no platform double-booked.` }
            ], valid);
        },
        priority() {
            startScenario();
            const waitingBefore = system.allocationEngine.getStatus().waitingQueue.map(item => item.trainId);
            const departure = system.allocationEngine.departTrain("T001");
            const status = system.allocationEngine.getStatus();
            const trainState = id => status.trains.find(train => train.id === id);
            const emergencyFirst = waitingBefore[0] === "T005"
                && trainState("T005").platformId === "PF-1"
                && trainState("T004").state === "WAITING";
            report("Emergency precedes scheduled train", [
                { passed: waitingBefore[0] === "T005", text: `Before release, queue order is ${waitingBefore.join(", ")}.` },
                { passed: departure.ok && emergencyFirst, text: "After T001 departs, emergency T005 gets PF-1 before scheduled T004." }
            ], emergencyFirst);
        }
    };

    document.querySelectorAll("[data-scenario]").forEach(button => {
        button.addEventListener("click", () => {
            try {
                scenarios[button.dataset.scenario]();
            } catch (error) {
                report("Scenario could not run", [
                    { passed: false, text: error instanceof Error ? error.message : String(error) }
                ], false);
                console.error(error);
            }
        });
    });

    document.getElementById("btnResetState").addEventListener("click", () => {
        system.reset();
        results.className = "scenario-result";
        results.replaceChildren();
        const heading = document.createElement("strong");
        heading.textContent = "System reset";
        const message = document.createElement("p");
        message.textContent = "Seeded Phase 2 state restored.";
        results.append(heading, message);
    });
})();
