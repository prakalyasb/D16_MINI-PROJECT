/**
 * Payanam Junction - Railway Reservation System
 * terminal-widget.js: Reusable terminal widget rendering Java console-style outputs.
 */

/**
 * Renders an array of text lines into a real terminal-styled UI container.
 * Auto-scrolls to the bottom and animates newly appended lines.
 *
 * @param {string} containerId - The DOM element ID to host the terminal
 * @param {string[]} lines - Array of log strings to display
 * @param {string} [title="CONSOLE EVENT STREAM"] - Optional custom terminal title
 */
function renderTerminal(containerId, lines, title = "CONSOLE EVENT STREAM") {
    const container = document.getElementById(containerId);
    if (!container) return;

    // Check if terminal shell is already built
    let shell = container.querySelector(".terminal-shell");
    let body = container.querySelector(".terminal-body");

    if (!shell || !body) {
        container.innerHTML = "";

        shell = document.createElement("div");
        shell.className = "terminal-shell";

        // Terminal Top Bar Chrome
        const header = document.createElement("div");
        header.className = "terminal-header";
        header.innerHTML = `
            <div class="terminal-controls">
                <span class="dot red"></span>
                <span class="dot yellow"></span>
                <span class="dot green"></span>
            </div>
            <div class="terminal-title">
                <span class="terminal-icon">&gt;_</span> ${title}
            </div>
            <div class="terminal-meta">
                <span class="live-indicator">LIVE</span>
            </div>
        `;

        body = document.createElement("div");
        body.className = "terminal-body";
        body.setAttribute("tabindex", "0");

        shell.appendChild(header);
        shell.appendChild(body);
        container.appendChild(shell);
    }

    // Render lines with syntax highlighting
    if (!lines || lines.length === 0) {
        body.innerHTML = `
            <div class="terminal-line placeholder">
                <span class="prompt-sym">&gt;</span> <span class="dim">Payanam Junction System ready. Awaiting reservation events...</span>
            </div>
        `;
        return;
    }

    // Format and render all lines
    const fragment = document.createDocumentFragment();
    lines.forEach((lineText, idx) => {
        const lineDiv = document.createElement("div");
        lineDiv.className = "terminal-line line-fade-in";

        // Highlight keywords to match high-tech terminal output
        let formatted = lineText;
        if (lineText.startsWith("BOOK:")) {
            formatted = `<span class="tag-book">BOOK:</span> ${escapeHtml(lineText.slice(5))}`;
        } else if (lineText.startsWith("CANCEL:")) {
            formatted = `<span class="tag-cancel">CANCEL:</span> ${escapeHtml(lineText.slice(7))}`;
        } else if (lineText.startsWith("PROMOTE:")) {
            formatted = `<span class="tag-promote">PROMOTE:</span> ${escapeHtml(lineText.slice(8))}`;
        } else if (lineText.startsWith("ERROR:") || lineText.startsWith("CANCEL ERROR:")) {
            formatted = `<span class="tag-error">ERROR:</span> ${escapeHtml(lineText.replace(/^(ERROR:|CANCEL ERROR:)/, ""))}`;
        } else if (lineText.startsWith("[SCENARIO:") || lineText.startsWith("[SYSTEM]")) {
            formatted = `<span class="tag-system">${escapeHtml(lineText)}</span>`;
        } else {
            formatted = escapeHtml(lineText);
        }

        lineDiv.innerHTML = `<span class="prompt-sym">&gt;</span> ${formatted}`;
        fragment.appendChild(lineDiv);
    });

    body.innerHTML = "";
    body.appendChild(fragment);

    // Auto-scroll to the bottom
    requestAnimationFrame(() => {
        body.scrollTop = body.scrollHeight;
    });
}

/**
 * Escapes HTML characters to prevent XSS injection
 */
function escapeHtml(str) {
    if (!str) return "";
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
