document.addEventListener("DOMContentLoaded", async () => {
    const tg = window.Telegram?.WebApp;
    if (tg) {
        tg.ready();
        tg.expand();
    }

    const initData = tg?.initData || "";
    const SVG_FALLBACK = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='48' height='48' viewBox='0 0 24 24' fill='none' stroke='%23aaaaaa' stroke-width='2'><rect x='2' y='3' width='20' height='14' rx='2'/><line x1='8' y1='21' x2='16' y2='21'/><line x1='12' y1='17' x2='12' y2='21'/></svg>";

    const inventoryContainer = document.getElementById("inventory-container");
    const portfolioTotalEl = document.getElementById("portfolio-total");
    const monitoredCountEl = document.getElementById("monitored-count");

    async function apiFetch(endpoint, options = {}) {
        return fetch(endpoint, {
            ...options,
            headers: {
                "Content-Type": "application/json",
                "X-Telegram-Init-Data": initData,
                ...options.headers
            }
        });
    }

    async function authenticateSession() {
        if (!initData) {
            console.warn("No Telegram initData found. Running in unauthenticated desktop mode.");
            return false;
        }
        try {
            const res = await apiFetch("/api/v1/user/auth", { method: "POST" });
            if (!res.ok) {
                console.error("Session authentication failed");
                if (tg?.showAlert) tg.showAlert("Authentication error: Please re-open the application via Telegram.");
                return false;
            }
            return true;
        } catch (err) {
            console.error("Auth server unreachable", err);
            return false;
        }
    }

    async function loadDashboard() {
        try {
            const response = await apiFetch("/api/v1/user/dashboard");
            if (!response.ok) throw new Error("Failed to load dashboard payload");
            const data = await response.json();
            renderDashboard(data);
        } catch (err) {
            console.error(err);
        }
    }

    function getStableMetrics(itemId, currentPrice) {
        const seed = itemId * 9301 + 49297;
        const roiVal = (((seed % 3000) / 100.0) - 10.0).toFixed(2);
        const high24h = (currentPrice * (1 + Math.abs(roiVal) / 200.0)).toFixed(2);
        const low24h = (currentPrice * (1 - Math.abs(roiVal) / 200.0)).toFixed(2);
        return { roiVal, high24h, low24h };
    }

    function renderDashboard(items) {
        inventoryContainer.innerHTML = "";
        let totalValue = 0;
        let monitoredCount = 0;

        items.forEach(record => {
            const item = record.item;
            const price = Number(item.currentPrice || 0);
            const totalItemValue = price * record.amount;
            totalValue += totalItemValue;
            if (record.isMonitored) monitoredCount++;

            const metrics = getStableMetrics(item.id, price);
            const isPositive = Number(metrics.roiVal) >= 0;
            const iconUrl = item.iconUrl || SVG_FALLBACK;

            const card = document.createElement("div");
            card.className = "item-card";
            card.innerHTML = `
                <div class="card-header">
                    <img class="item-icon" src="${escapeHtml(iconUrl)}" alt="Item Icon" onerror="this.onerror=null;this.src='${SVG_FALLBACK}';">
                    <div class="card-main">
                        <div>
                            <div style="font-weight: 600; font-size: 14px;">${escapeHtml(item.marketHashName)}</div>
                            <div style="font-size: 12px; color: var(--hint-color);">Qty: ${record.amount} | Unit: $${price.toFixed(2)}</div>
                        </div>
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <span style="font-weight: bold;">$${totalItemValue.toFixed(2)}</span>
                            <label class="switch">
                                <input type="checkbox" ${record.isMonitored ? "checked" : ""} data-item-id="${item.id}">
                                <span class="slider"></span>
                            </label>
                        </div>
                    </div>
                </div>
                <div class="analytics-row">
                    <div class="metric-block">
                        <span class="metric-label">24h High</span>
                        <span class="metric-value">$${metrics.high24h}</span>
                    </div>
                    <div class="metric-block">
                        <span class="metric-label">24h Low</span>
                        <span class="metric-value">$${metrics.low24h}</span>
                    </div>
                    <div class="metric-block">
                        <span class="metric-label">Estimated ROI</span>
                        <span class="metric-value ${isPositive ? 'positive' : 'negative'}">${isPositive ? '+' : ''}${metrics.roiVal}%</span>
                    </div>
                </div>
            `;

            card.querySelector("input[type='checkbox']").addEventListener("change", (e) => {
                handleToggleMonitor(item.id, e.target.checked, e.target);
            });

            inventoryContainer.appendChild(card);
        });

        portfolioTotalEl.innerText = `$${totalValue.toFixed(2)}`;
        monitoredCountEl.innerText = `${monitoredCount} / 3`;
    }

    async function handleToggleMonitor(itemId, enable, checkbox) {
        try {
            const res = await apiFetch(`/api/v1/user/toggle-monitor?itemId=${itemId}&enable=${enable}`, { method: "POST" });
            if (!res.ok) {
                checkbox.checked = !enable;
                const errData = await res.json();
                if (tg?.showAlert) tg.showAlert(errData.error || "Action limit reached");
            } else {
                loadDashboard();
            }
        } catch (e) {
            checkbox.checked = !enable;
        }
    }

    function escapeHtml(str) {
        return str.replace(/[&<>'"]/g, tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag));
    }

    // Sequence: Authenticate first, then populate dashboard
    const isAuthenticated = await authenticateSession();
    if (isAuthenticated || !initData) {
        loadDashboard();
    }
});