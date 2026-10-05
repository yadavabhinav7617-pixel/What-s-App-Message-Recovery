const state = { token: localStorage.getItem('nv_admin_token') || '', backend: 'http://localhost:4000' };
const el = (id) => document.getElementById(id);

const formatNumber = (value) => Number(value || 0).toLocaleString();
const formatDate = (value) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
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

async function renderDashboard() {
  try {
    const [summary, devices, messages] = await Promise.all([
      api('/api/admin/dashboard'),
      api('/api/admin/devices'),
      api('/api/admin/messages?limit=30')
    ]);

    const summaryCards = [
      { label: 'Total Devices', value: formatNumber(summary.totalRegisteredDevices || 0), tone: 'primary' },
      { label: 'Online Devices', value: formatNumber(summary.onlineDevices || 0), tone: 'success' },
      { label: 'Offline Devices', value: formatNumber(summary.offlineDevices || 0), tone: 'warning' },
      { label: 'Total Messages', value: formatNumber(summary.totalSynchronizedMessages || 0), tone: 'info' },
      { label: "Today's Messages", value: formatNumber(summary.todaysMessages || 0), tone: 'primary' },
      { label: 'Pending Sync', value: '—', tone: 'neutral' }
    ];

    const renderTable = (rows) => rows.length ? rows : [{ empty: true }];

    document.getElementById('app').innerHTML = `
      <div class="container dashboard-shell">
        <header class="topbar">
          <div>
            <div class="eyebrow">Operations</div>
            <h1>Admin Dashboard</h1>
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
              <h2>Devices</h2>
              <span class="chip neutral">${(devices.devices || []).length} tracked</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>Device</th><th>Online</th><th>Last seen</th><th>Last sync</th><th>Status</th></tr>
                </thead>
                <tbody>
                  ${(renderTable(devices.devices || [])).map(device => device.empty ? '<tr><td colspan="5">No devices registered</td></tr>' : `
                    <tr>
                      <td>${device.deviceName || device.deviceId}</td>
                      <td><span class="status-pill ${device.online ? 'online' : 'offline'}">${device.online ? 'Online' : 'Offline'}</span></td>
                      <td>${formatDate(device.lastSeen)}</td>
                      <td>${formatDate(device.lastSync)}</td>
                      <td>${device.status || 'ACTIVE'}</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>

          <div class="card panel-card">
            <div class="panel-header">
              <h2>Recent Messages</h2>
              <span class="chip info">${(messages.messages || []).length} shown</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>Sender</th><th>Device</th><th>Message</th><th>Captured</th></tr>
                </thead>
                <tbody>
                  ${(renderTable(messages.messages || [])).map(msg => msg.empty ? '<tr><td colspan="4">No messages</td></tr>' : `
                    <tr>
                      <td>${msg.sender || 'Unknown'}</td>
                      <td>${msg.deviceId || 'device'}</td>
                      <td>${(msg.messageText || '').slice(0, 80)}</td>
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

    const source = new EventSource(`${state.backend}/api/admin/stream`, { withCredentials: true });
    source.onmessage = async (event) => {
      const data = JSON.parse(event.data || '{}');
      if (data.type === 'new_message') {
        try {
          const refreshed = await api('/api/admin/messages?limit=30');
          const rows = document.querySelectorAll('tbody tr');
          if (rows.length) {
            const table = rows[0].closest('table');
            if (table) {
              table.tBodies[0].innerHTML = (refreshed.messages || []).map(msg => `
                <tr>
                  <td>${msg.sender || 'Unknown'}</td>
                  <td>${msg.deviceId || 'device'}</td>
                  <td>${(msg.messageText || '').slice(0, 80)}</td>
                  <td>${formatDate(msg.capturedAt)}</td>
                </tr>
              `).join('') || '<tr><td colspan="4">No messages</td></tr>';
            }
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
