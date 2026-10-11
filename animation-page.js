(() => {
    const system = window.railwaySystem;
    const engine = system.allocationEngine;
    const byId = id => document.getElementById(id);
    const minute = value => window.parseTimeToMinutes(value);
    const clock = value => window.minutesToTime(value);
    const trains = () => system.listAllTrains();
    let playTimer = null;
    let playStep = 0;
    let highlightedSeat = null;

    function showResult(elementId, result) {
        const element = byId(elementId);
        const kind = result.rejected || (!result.ok && !result.queued)
            ? "error"
            : result.queued ? "warning" : result.ok ? "success" : "info";
        const banner = document.createElement("div");
        banner.className = `status-msg ${kind}`;
        banner.textContent = result.message || result.reason || "Action completed.";
        element.replaceChildren(banner);
    }

    function refreshSelects() {
        const options = trains().map(train => `<option value="${train.id}">${train.id} — ${train.name}</option>`).join("");
        ["placementTrain", "overlapTrain", "delayTrain", "seatTrain"].forEach(id => {
            const select = byId(id);
            const previous = select.value;
            select.innerHTML = options;
            if (previous && system.trains.has(previous)) select.value = previous;
        });
    }

    function renderTrack() {
        const status = engine.getStatus();
        byId("trackLanes").innerHTML = status.platforms.map(platform => `
            <div class="sim-lane" data-label="${platform.id} · ${platform.length}m">
                ${platform.occupiedIntervals.map(interval => {
                    const train = system.trains.get(interval.trainId);
                    if (!train) return "";
                    const start = interval.startMin;
                    const end = interval.endMin - engine.bufferMinutes;
                    const classes = train.priority === "EMERGENCY" ? "emergency" : train.priority === "CONNECTING" ? "connecting" : "";
                    return `<div class="train-bar ${classes}" style="left:${start / 1440 * 100}%;width:${Math.max((end - start + engine.bufferMinutes) / 1440 * 100, .7)}%">${train.id} ${clock(start)}–${clock(end)}</div>`;
                }).join("")}
            </div>`).join("");

        byId("trackStaging").replaceChildren();
        status.trains.filter(train => !train.platformId).forEach(train => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "sim-chip";
            button.textContent = `${train.id} · ${train.state}${train.queuePosition ? ` (#${train.queuePosition})` : ""} — Request platform`;
            button.addEventListener("click", () => {
                showResult("trackFeedback", engine.requestPlatform(train.id));
                renderAll();
            });
            byId("trackStaging").append(button);
        });
        status.trains.filter(train => train.platformId).forEach(train => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "sim-chip";
            button.textContent = `${train.id} · ${train.platformId} — Depart`;
            button.addEventListener("click", () => {
                showResult("trackFeedback", engine.departTrain(train.id));
                renderAll();
            });
            byId("trackStaging").append(button);
        });
    }

    function renderOverlap() {
        const status = engine.getStatus();
        byId("overlapLanes").innerHTML = status.platforms.map(platform => `
            <div class="sim-lane" data-label="${platform.id} · ${platform.length}m">
                ${platform.occupiedIntervals.map(interval => {
                    const train = system.trains.get(interval.trainId);
                    if (!train) return "";
                    return `<div class="train-bar" style="left:${interval.startMin / 1440 * 100}%;width:${Math.max((interval.endMin - interval.startMin) / 1440 * 100, .7)}%">${train.id}</div>`;
                }).join("")}
            </div>`).join("");
    }

    function renderSeats() {
        const train = system.trains.get(byId("seatTrain").value);
        if (!train) return;
        byId("seatGrid").innerHTML = train.seats.map((reservation, index) =>
            `<div class="sim-seat${reservation ? " booked" : ""}${highlightedSeat === index + 1 ? " promoted" : ""}">Seat ${index + 1}<br>${reservation ? `${reservation.passenger.name}<br>${reservation.passenger.id}` : "Free"}</div>`
        ).join("");
        if (!byId("cancelPassenger").value) {
            const first = train.seats.find(Boolean);
            if (first) byId("cancelPassenger").value = first.passenger.id;
        }
    }

    function renderEvents() {
        const list = byId("eventTimeline");
        list.replaceChildren();
        window.eventLog.slice(-30).reverse().forEach(line => {
            const item = document.createElement("li");
            item.textContent = line;
            list.append(item);
        });
    }

    function renderAll() {
        refreshSelects();
        const status = engine.getStatus();
        byId("simUtilization").textContent = `${status.utilizationPercentage}%`;
        byId("simAssigned").textContent = String(status.trains.filter(train => train.platformId).length);
        byId("simWaiting").textContent = String(status.waitingQueue.length);
        renderTrack();
        renderOverlap();
        renderSeats();
        renderEvents();
    }

    function syncTimeInputs(trainSelectId, arrivalId, departureId) {
        const train = system.trains.get(byId(trainSelectId).value);
        if (!train) return;
        byId(arrivalId).value = train.arrival;
        byId(departureId).value = train.departure;
    }

    document.querySelectorAll(".sim-tab").forEach(tab => tab.addEventListener("click", () => {
        document.querySelectorAll(".sim-tab").forEach(item => {
            const selected = item === tab;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-selected", String(selected));
        });
        document.querySelectorAll(".sim-panel").forEach(panel => {
            panel.hidden = panel.id !== `panel-${tab.dataset.tab}`;
        });
    }));

    byId("placementTrain").addEventListener("change", () => syncTimeInputs("placementTrain", "placementArrival", "placementDeparture"));
    byId("placementForm").addEventListener("submit", event => {
        event.preventDefault();
        const train = system.trains.get(byId("placementTrain").value);
        const arrival = minute(byId("placementArrival").value);
        const departure = minute(byId("placementDeparture").value);
        const result = departure <= arrival
            ? { ok: false, message: "Departure must be later than arrival." }
            : engine.moveTrain(train.id, byId("placementPlatform").value, arrival, departure);
        showResult("trackFeedback", result);
        renderAll();
    });

    byId("overlapTrain").addEventListener("change", () => syncTimeInputs("overlapTrain", "overlapArrival", "overlapDeparture"));
    byId("overlapForm").addEventListener("submit", event => {
        event.preventDefault();
        const train = system.trains.get(byId("overlapTrain").value);
        const arrival = minute(byId("overlapArrival").value);
        const departure = minute(byId("overlapDeparture").value);
        const result = departure <= arrival
            ? { ok: false, message: "Departure must be later than arrival." }
            : engine.previewPlacement(train.id, byId("overlapPlatform").value, arrival, departure, byId("overlapBuffer").checked);
        showResult("overlapFeedback", result);
        renderOverlap();
    });

    byId("delayTrain").addEventListener("change", () => {
        const train = system.trains.get(byId("delayTrain").value);
        byId("delayFeedback").textContent = train ? `Current schedule: ${train.arrival}–${train.departure}.` : "";
    });
    byId("delayForm").addEventListener("submit", event => {
        event.preventDefault();
        const train = system.trains.get(byId("delayTrain").value);
        const delay = Number(byId("delayMinutes").value);
        const arrival = minute(train.arrival) + delay;
        const departure = minute(train.departure) + delay;
        if (!Number.isInteger(delay) || delay < 1 || delay > 720 || departure > 1440) {
            showResult("delayFeedback", { ok: false, message: "Enter a delay from 1 to 720 minutes that keeps the schedule before midnight." });
            return;
        }
        const result = engine.delayTrain(train.id, arrival, departure);
        showResult("delayFeedback", result);
        renderAll();
    });

    byId("seatTrain").addEventListener("change", () => {
        highlightedSeat = null;
        byId("cancelPassenger").value = "";
        renderSeats();
    });
    byId("prepareSeats").addEventListener("click", () => {
        const train = system.trains.get(byId("seatTrain").value);
        let passengerNumber = 1;
        train.seats.forEach((reservation, index) => {
            if (reservation) return;
            let passengerId;
            do {
                passengerId = `SIM-P${String(passengerNumber++).padStart(3, "0")}`;
            } while (system.passengers.has(passengerId));
            system.bookSeat(train.id, passengerId, `Simulator passenger ${passengerNumber - 1}`, index + 1);
        });
        let waiterNumber = 1;
        while (train.waitingQueue.length < 2) {
            let passengerId;
            do {
                passengerId = `SIM-W${String(waiterNumber++).padStart(3, "0")}`;
            } while (system.passengers.has(passengerId));
            system.joinWaitingList(train.id, passengerId, `Waiting passenger ${waiterNumber - 1}`);
        }
        byId("cancelPassenger").value = train.seats.find(Boolean)?.passenger.id || "";
        showResult("seatFeedback", { ok: true, message: `${train.id} is ready. Cancel a booked passenger to promote the first waiter.` });
        renderAll();
    });
    byId("cancelByPassenger").addEventListener("click", () => {
        const trainId = byId("seatTrain").value;
        const result = system.cancelBookingByPassengerId(trainId, byId("cancelPassenger").value.trim());
        highlightedSeat = result.ok ? result.cancelledSeat : null;
        const message = result.ok
            ? `Cancelled seat ${result.cancelledSeat}${result.promoted ? `; ${result.promoted.passenger.name} promoted to that seat` : ""}.`
            : result.reason;
        showResult("seatFeedback", { ...result, message });
        renderAll();
    });

    const autoSteps = [
        () => {
            const assignedTrain = Array.from(engine.assignments.keys())[0];
            return assignedTrain ? engine.departTrain(assignedTrain) : engine.retryNextWaiting();
        },
        () => engine.retryNextWaiting(),
        () => {
            const train = system.trains.get("T003");
            return engine.delayTrain(train.id, minute(train.arrival) + 5, minute(train.departure) + 5);
        }
    ];
    function runAutoStep() {
        if (playStep >= autoSteps.length) {
            clearInterval(playTimer);
            playTimer = null;
            byId("playAuto").textContent = "▶ Play";
            showResult("autoFeedback", { ok: true, message: "Dispatch sequence complete." });
            return;
        }
        const result = autoSteps[playStep++]();
        byId("simClock").textContent = `${playStep} / ${autoSteps.length}`;
        showResult("autoFeedback", result);
        renderAll();
        if (playStep >= autoSteps.length) {
            clearInterval(playTimer);
            playTimer = null;
            byId("playAuto").textContent = "▶ Play";
        }
    }
    byId("playAuto").addEventListener("click", () => {
        if (playTimer) {
            clearInterval(playTimer);
            playTimer = null;
            byId("playAuto").textContent = "▶ Play";
            return;
        }
        if (playStep >= autoSteps.length) playStep = 0;
        byId("playAuto").textContent = "⏸ Pause";
        runAutoStep();
        if (playStep < autoSteps.length) {
            playTimer = setInterval(runAutoStep, Number(byId("playSpeed").value));
        }
    });
    byId("playSpeed").addEventListener("change", () => {
        if (!playTimer) return;
        clearInterval(playTimer);
        playTimer = setInterval(runAutoStep, Number(byId("playSpeed").value));
    });
    function resetSystem() {
        if (playTimer) clearInterval(playTimer);
        playTimer = null;
        playStep = 0;
        highlightedSeat = null;
        byId("playAuto").textContent = "▶ Play";
        byId("simClock").textContent = `0 / ${autoSteps.length}`;
        system.reset();
        document.querySelectorAll(".sim-feedback").forEach(element => element.replaceChildren());
        renderAll();
        syncTimeInputs("placementTrain", "placementArrival", "placementDeparture");
        syncTimeInputs("overlapTrain", "overlapArrival", "overlapDeparture");
    }
    byId("resetAuto").addEventListener("click", resetSystem);
    byId("btnResetState").addEventListener("click", resetSystem);

    renderAll();
    syncTimeInputs("placementTrain", "placementArrival", "placementDeparture");
    syncTimeInputs("overlapTrain", "overlapArrival", "overlapDeparture");
})();
