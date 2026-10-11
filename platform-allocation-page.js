(() => {
    const system = window.railwaySystem;
    const engine = system.allocationEngine;
    const resultElement = document.getElementById("platformActionResult");
    const timeToMinutes = window.parseTimeToMinutes;

    function showResult(result) {
        const type = result.rejected || (!result.ok && !result.queued)
            ? "error"
            : result.queued ? "warning" : "success";
        const message = document.createElement("div");
        message.className = `status-msg ${type}`;
        message.textContent = result.message || result.reason || "Action completed.";
        resultElement.replaceChildren(message);
        render();
    }

    function render() {
        const status = engine.getStatus();
        const trains = system.listAllTrains();
        const options = trains.map(train => `<option value="${train.id}">${train.id} — ${train.name}</option>`).join("");
        document.getElementById("requestTrain").innerHTML = options;
        document.getElementById("delayTrain").innerHTML = options;

        const assigned = status.trains.filter(train => train.platformId);
        document.getElementById("departTrain").innerHTML = assigned.length
            ? assigned.map(train => `<option value="${train.id}">${train.id} — ${train.platformId}</option>`).join("")
            : '<option value="">No train currently assigned</option>';
        document.getElementById("occupiedCount").textContent =
            `${status.platforms.filter(platform => platform.occupiedIntervals.length).length} / ${status.platforms.length}`;
        document.getElementById("utilization").textContent = `${status.utilizationPercentage}%`;
        document.getElementById("waitingCount").textContent = String(status.waitingQueue.length);

        document.getElementById("platformCards").innerHTML = status.platforms.map(platform => `
            <article class="platform-card">
                <h3>${platform.id} <span class="train-detail-val">${platform.length}m</span></h3>
                <div class="timeline" aria-label="${platform.id} 24-hour schedule">
                    ${platform.occupiedIntervals.map(interval => {
                        const left = interval.startMin / 1440 * 100;
                        const width = (interval.endMin - interval.startMin) / 1440 * 100;
                        return `<div class="timeline-bar" style="left:${left}%;width:${Math.max(width, 1)}%">${interval.trainId} ${window.minutesToTime(interval.startMin)}-${window.minutesToTime(interval.endMin)}</div>`;
                    }).join("")}
                </div>
                <div class="timeline-labels"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>
                <p class="train-detail-label">${platform.occupiedIntervals.length} occupied interval(s)</p>
            </article>`).join("");

        document.getElementById("platformQueue").innerHTML = status.waitingQueue.length
            ? status.waitingQueue.map(item => `<div class="phase2-row"><span><strong>#${item.position} ${item.trainId}</strong> · ${item.arrival}–${item.departure}</span><span class="priority-badge ${item.priority.toLowerCase()}">${item.priority}</span></div>`).join("")
            : '<div class="status-msg info">No trains are waiting for a platform.</div>';
    }

    document.getElementById("requestForm").addEventListener("submit", event => {
        event.preventDefault();
        showResult(engine.requestPlatform(document.getElementById("requestTrain").value));
    });

    document.getElementById("departForm").addEventListener("submit", event => {
        event.preventDefault();
        const trainId = document.getElementById("departTrain").value;
        if (trainId) showResult(engine.departTrain(trainId));
    });

    document.getElementById("delayForm").addEventListener("submit", event => {
        event.preventDefault();
        const train = system.trains.get(document.getElementById("delayTrain").value);
        const delay = Number(document.getElementById("delayMinutes").value);
        if (!train || !Number.isInteger(delay) || delay < 1 || delay > 720) {
            showResult({ ok: false, message: "Enter a delay between 1 and 720 minutes." });
            return;
        }
        const arrival = timeToMinutes(train.arrival) + delay;
        const departure = timeToMinutes(train.departure) + delay;
        if (departure > 1440) {
            showResult({ ok: false, message: "The delayed schedule must end before midnight." });
            return;
        }
        showResult(engine.delayTrain(train.id, arrival, departure));
    });

    document.getElementById("btnResetState").addEventListener("click", () => {
        system.reset();
        resultElement.replaceChildren();
        render();
    });

    render();
})();
