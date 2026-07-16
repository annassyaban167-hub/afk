// --- API Helpers ---
const API = {
  get: url => fetch(url).then(r => r.json().catch(() => ({error: r.statusText})).then(d => ({...d, status: r.status}))),
  post: (url, body) => fetch(url, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body) })
    .then(r => r.json().catch(() => ({error: r.statusText})).then(d => ({...d, status: r.status}))),
  put: (url, body) => fetch(url, { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body) })
    .then(r => r.json().catch(() => ({error: r.statusText})).then(d => ({...d, status: r.status}))),
  del: url => fetch(url, { method: 'DELETE' })
    .then(r => r.json().catch(() => ({error: r.statusText})).then(d => ({...d, status: r.status}))),
};

// --- State ---
let statusPollInterval = null;
let currentAppId = null;

// Static list of stylized app icons for high-fidelity rendering
const APP_ICONS = {
  whatsapp: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDoyrayH-ET8JX5ESROawgh_Kn2tjE6-7iN1coKMVz_-qn0TtQUm71pR6K7XRJm6cxCblge_A9bgFsPudM2GHhxDp_doT2ZzBBfUHIVPqaQa_Xax0WYLPMmk2iPmr8vKWH64aa21C4qxHeXEe-r8Ec2u25GutfiDGAKjoSFVIDGYRxwISm2-sQRamnm0fBkm9zfxwB2oDD-Z-lEDlfFXg7Cw7q0LbjRvSr1fwUJ8G0PR4psh3nX4Tt6',
  youtube: 'https://lh3.googleusercontent.com/aida-public/AB6AXuC6qNLok2GjfBF_wftTwz-ylXcDrRUlvA1nLvHDwpM8saK_GgP5n4osQZeYQb7AXaJaulGQpptLLIyFlNtXd2wL2GSB9Vd-MRIWksGYS_sC-ABIUmK5yT8i77-Y58FqMZwzmp37uSPjA0ddggfiO7OBysC4zJPzLTumDxCzzmZc9kFVCsJ7GcwhOeOyM9fuvZVAVaAmT7QhzWZIQz1OsWCVQZJdW_t2hQ44hB5PVIU19Bq_EkHlQAAC',
  instagram: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBWJMgqWBN0-AKsn8lAs91sfLjwfkjUIfsJ5jUE3ignK9S9hAI4MsEZ4sUugqfYDM0ZkWam5sq7cxp3xRQKGq9jsTFX8j79-kuyzenkyX_UZN21ClAA9PpJ8Pz7p_-ZwZDGTsdLqnbJ-it55-7iyel77PjY6LQ3R4LmhR4gcOvRrMiWtDJFWu44UBmthlmGZ-0uaoRXA2IaMt-VB4g2ZZjhGFxXyOOxGSlRv6v46a6RNnkMaZFA1j07',
  tiktok: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCsUoVvnkoFm7hbam0vo7cd31kMxLxyUs8No2p0eG87t_nZgKCqS6k9mx7F6PyNHc-TxXM2rmdtxxSlmqBBhe6y7F7rrt476t7rPh5o4q8YmiZl56RzzWqVE1Z6m022jD-O6JSWn8TSHQsR5Rx7YLa59Ae959WUPMDS0yT4jrWHtEgvbLaRh4VmDRXR9snMSYeVKbzC-s2bi9saXCQNiFyTUATW0e5FA6JS_aN57Euza1AJOIZ1mbBZ',
  fallback: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="%23ddb7ff" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M12 8v8"/><path d="M8 12h8"/></svg>'
};

function getAppIcon(name) {
  const clean = name.toLowerCase();
  for (const key in APP_ICONS) {
    if (clean.includes(key)) return APP_ICONS[key];
  }
  return APP_ICONS.fallback;
}

// --- Navigation / Routing ---
function router() {
  const hash = location.hash || '#dashboard';

  // Support legacy routing mapping
  let targetSection = hash.slice(1);
  if (targetSection === 'home') targetSection = 'dashboard';
  if (targetSection === 'global-settings') targetSection = 'settings';

  // Toggle active pages
  document.querySelectorAll('[data-section]').forEach(sec => {
    sec.classList.add('hidden');
  });

  const activeSec = document.querySelector(`[data-section="${targetSection}"]`);
  if (activeSec) {
    activeSec.classList.remove('hidden');
  } else {
    // Default fallback
    const fallbackSec = document.querySelector('[data-section="dashboard"]');
    if (fallbackSec) fallbackSec.classList.remove('hidden');
  }

  // Highlight side navigation items
  document.querySelectorAll('[data-nav]').forEach(btn => {
    const navDest = btn.getAttribute('data-nav');
    const isMatching = (navDest === targetSection) ||
                       (navDest === 'add-app' && hash === '#add-app');

    if (isMatching) {
      btn.className = 'nav-link flex items-center gap-sm px-md py-sm bg-primary/10 text-primary border-r-4 border-primary font-body-md transition-all duration-300';
    } else {
      btn.className = 'nav-link flex items-center gap-sm px-md py-sm text-on-surface-variant hover:bg-white/5 font-body-md transition-all duration-300';
    }
  });

  // Action based on route
  if (targetSection === 'dashboard') {
    renderDashboard();
  } else if (hash === '#add-app') {
    // Map '#add-app' route to open the Add App Modal directly
    location.hash = '#dashboard';
    openAddModal();
  } else if (targetSection === 'settings') {
    renderGlobalSettings();
  }
}

// --- Dashboard Rendering ---
async function renderDashboard() {
  const grid = document.getElementById('apps-grid');
  grid.innerHTML = '<div class="col-span-full text-center py-xl text-on-surface-variant">Loading apps...</div>';

  // Update ADB & Device Connection status
  updateConnectionStatus();

  // Load apps list
  const res = await API.get('/api/apps');

  // Update Overview counter statistics
  const appCount = res.apps ? res.apps.length : 0;
  document.getElementById('stat-apps-count').textContent = appCount.toString();

  if (!res.apps || res.apps.length === 0) {
    grid.innerHTML = `
      <div class="col-span-full text-center py-xl text-on-surface-variant glass-card rounded-xl">
        <p class="mb-sm">No apps added yet.</p>
        <button onclick="openAddModal()" class="py-xs px-md rounded-lg primary-gradient-btn text-white font-bold text-label-sm">Add First App</button>
      </div>
    `;
    return;
  }

  grid.innerHTML = '';
  res.apps.forEach(app => {
    const card = document.createElement('div');
    card.className = 'glass-card rounded-xl p-md flex flex-col items-center text-center group cursor-pointer hover:scale-[1.02]';

    // We fetch current status of this specific card
    const hasLimit = app.durationMin && app.durationMin > 0;

    card.innerHTML = `
      <div class="relative mb-md">
        <div class="w-16 h-16 rounded-2xl overflow-hidden shadow-lg bg-surface/40 flex items-center justify-center border border-white/5">
          <img class="w-full h-full object-cover" src="${getAppIcon(app.name)}" alt="${escHtml(app.name)}">
        </div>
        <div class="active-indicator absolute -bottom-1 -right-1 w-5 h-5 bg-on-surface-variant rounded-full border-4 border-surface flex items-center justify-center hidden">
          <div class="w-1.5 h-1.5 bg-surface rounded-full animate-ping"></div>
        </div>
      </div>
      <h4 class="font-label-md text-headline-md text-on-surface mb-xs font-semibold">${escHtml(app.name)}</h4>
      <span class="status-badge px-sm py-xs rounded-full bg-white/5 text-on-surface-variant text-label-sm mb-md block">
        ${hasLimit ? `Limit: ${app.durationMin}m` : 'Tracking Only'}
      </span>
      <div class="w-full pt-md border-t border-white/5 flex justify-between items-center gap-xs">
        <span class="text-label-sm text-on-surface-variant truncate font-mono text-[11px]">${escHtml(app.packageName)}</span>
        <div class="flex gap-xs">
          <button class="edit-btn p-xs text-on-surface-variant hover:text-primary transition-colors">
            <span class="material-symbols-outlined text-[18px]">edit</span>
          </button>
          <button class="del-btn p-xs text-on-surface-variant hover:text-error transition-colors">
            <span class="material-symbols-outlined text-[18px]">delete</span>
          </button>
        </div>
      </div>
    `;

    // Click card opens drawer
    card.addEventListener('click', (e) => {
      // Prevent drawer if clicking actions
      if (e.target.closest('.edit-btn') || e.target.closest('.del-btn')) return;
      openAppMonitor(app.id);
    });

    // Actions
    card.querySelector('.edit-btn').onclick = (e) => {
      e.stopPropagation();
      openEditModal(app.id, app.name, app.packageName);
    };

    card.querySelector('.del-btn').onclick = async (e) => {
      e.stopPropagation();
      if (!confirm(`Remove "${app.name}"?`)) return;
      await API.del(`/api/apps/${app.id}`);
      renderDashboard();
    };

    grid.appendChild(card);
  });

  // Check if any app is active, highlight on grid
  updateActiveAppHighlight();
}

async function updateActiveAppHighlight() {
  const status = await API.get('/api/monitor/status');
  document.getElementById('stat-active-count').textContent = status.active ? '1' : '0';

  const overviewText = document.getElementById('monitoring-status-text');
  if (status.active) {
    overviewText.innerHTML = `Active session: <span class="text-primary font-semibold font-mono">${escHtml(status.packageName)}</span>`;
  } else {
    overviewText.textContent = 'No active monitoring sessions.';
  }

  // Update card animations/badges
  document.querySelectorAll('#apps-grid > div').forEach(card => {
    const pkg = card.querySelector('span.font-mono').textContent;
    const indicator = card.querySelector('.active-indicator');
    const badge = card.querySelector('.status-badge');

    if (status.active && pkg === status.packageName) {
      indicator.classList.remove('hidden');
      indicator.classList.remove('bg-on-surface-variant');
      indicator.classList.add('bg-primary');
      badge.className = 'status-badge px-sm py-xs rounded-full bg-primary/20 text-primary text-label-sm mb-md block font-semibold';
      badge.textContent = 'MONITORED';
    } else {
      indicator.classList.add('hidden');
    }
  });
}

// --- Connection Status Sync ---
async function updateConnectionStatus() {
  const status = await API.get('/api/monitor/status');
  const hasPass = await API.get('/api/settings/has-password');

  // We fetch settings directly if password is not set yet, otherwise with mock PIN header if unlocked
  let adbAddress = 'Not set';
  if (!hasPass.hasPassword) {
    const settings = await API.get('/api/settings');
    adbAddress = settings.adbAddress || 'Not set';
  } else {
    // Attempt load if cached PIN exists, or show gate
    const pin = document.getElementById('input-pin-gate').value || '';
    if (pin.length === 6) {
      const res = await fetch('/api/settings', { headers: { 'X-Password': pin } });
      if (res.ok) {
        const settings = await res.json();
        adbAddress = settings.adbAddress || 'Not set';
      }
    }
  }

  const badge = document.getElementById('adb-status-badge');
  const statusText = document.getElementById('adb-status-text');
  const termOut = document.getElementById('adb-terminal-output');
  const devBox = document.getElementById('device-status-box');
  const devDot = document.getElementById('device-status-dot');
  const devText = document.getElementById('device-status-text');

  if (adbAddress !== 'Not set') {
    // Presuming connected since address is stored (the backend reconnects)
    badge.className = 'px-sm py-xs rounded-full bg-secondary/10 text-secondary text-label-sm border border-secondary/20';
    badge.textContent = 'CONNECTED';
    statusText.textContent = `Wireless ADB linked on ${adbAddress}`;
    termOut.textContent = `> adb connect ${adbAddress}\nconnected to ${adbAddress}`;

    devBox.className = 'flex items-center gap-sm glass-card px-md py-sm rounded-xl border border-secondary/30';
    devDot.className = 'w-3 h-3 rounded-full bg-secondary status-glow-connected animate-pulse';
    devText.className = 'font-label-md text-secondary';
    devText.textContent = `ADB Device Connected`;
  } else {
    badge.className = 'px-sm py-xs rounded-full bg-white/5 text-on-surface-variant text-label-sm border border-white/10';
    badge.textContent = 'DISCONNECTED';
    statusText.textContent = 'No ADB destination address configured.';
    termOut.textContent = '> adb devices\nList of devices attached\n(empty)';

    devBox.className = 'flex items-center gap-sm glass-card px-md py-sm rounded-xl';
    devDot.className = 'w-3 h-3 rounded-full bg-on-surface-variant';
    devText.className = 'font-label-md text-on-surface-variant';
    devText.textContent = 'No Device Connected';
  }
}

// --- App Monitoring Drawer ---
async function openAppMonitor(id) {
  currentAppId = id;
  const res = await API.get('/api/apps');
  const app = res.apps ? res.apps.find(a => a.id === id) : null;
  if (!app) return;

  document.getElementById('drawer-app-name').textContent = app.name;
  document.getElementById('drawer-package-name').textContent = app.packageName;
  document.getElementById('input-duration').value = app.durationMin || 30;

  const startBtn = document.getElementById('btn-start-monitor');
  const stopBtn = document.getElementById('btn-stop-monitor');
  const statusDot = document.getElementById('monitor-status-dot');
  const statusText = document.getElementById('monitor-status-text');
  const statusDetail = document.getElementById('monitor-status-detail');

  // Reset Drawer controls UI
  statusDot.className = 'w-3 h-3 rounded-full bg-on-surface-variant';
  statusText.textContent = 'Not Monitoring';
  statusDetail.textContent = 'Click start to begin monitoring.';
  stopBtn.classList.add('hidden');
  startBtn.classList.remove('hidden');
  startBtn.disabled = false;

  // Show Drawer UI
  document.getElementById('app-drawer').classList.remove('hidden');

  // Check current monitor status
  const status = await API.get('/api/monitor/status');
  if (status.active && status.appId === id) {
    setDrawerMonitoringActive(status);
    startBtn.classList.add('hidden');
    stopBtn.classList.remove('hidden');
    startDrawerStatusPoll(id);
  }

  // Bind start/stop events
  startBtn.onclick = async () => {
    const dur = parseInt(document.getElementById('input-duration').value, 10);
    if (isNaN(dur) || dur < 1) return alert('Enter a valid duration (1-1440 mins).');

    startBtn.disabled = true;
    statusText.textContent = 'Connecting via ADB...';
    statusDetail.textContent = 'Waking up ADB server...';

    const result = await API.post('/api/monitor/start', { appId: id, durationMin: dur });
    if (result.error) {
      alert('Error: ' + result.error);
      startBtn.disabled = false;
      statusText.textContent = 'Start Failed';
      statusDetail.textContent = result.error;
      return;
    }

    stopBtn.classList.remove('hidden');
    startBtn.classList.add('hidden');
    startDrawerStatusPoll(id);
    renderDashboard();
  };

  stopBtn.onclick = async () => {
    await API.post('/api/monitor/stop');
    stopDrawerStatusPoll();

    statusDot.className = 'w-3 h-3 rounded-full bg-on-surface-variant';
    statusText.textContent = 'Stopped';
    statusDetail.textContent = 'Monitoring was ended manually.';
    stopBtn.classList.add('hidden');
    startBtn.classList.remove('hidden');
    startBtn.disabled = false;
    renderDashboard();
  };
}

function closeAppMonitor() {
  stopDrawerStatusPoll();
  document.getElementById('app-drawer').classList.add('hidden');
}

function setDrawerMonitoringActive(status) {
  const statusDot = document.getElementById('monitor-status-dot');
  const statusText = document.getElementById('monitor-status-text');
  const statusDetail = document.getElementById('monitor-status-detail');

  statusDot.className = 'w-3 h-3 rounded-full bg-primary status-glow-active animate-pulse';
  statusText.textContent = 'Monitoring Active';

  if (status.remainingSec > 0) {
    const min = Math.floor(status.remainingSec / 60);
    const sec = status.remainingSec % 60;
    statusDetail.textContent = `Stopping app in ${min}:${sec.toString().padStart(2, '0')}`;
  } else {
    statusDetail.textContent = 'Stopping app...';
  }
}

function startDrawerStatusPoll(id) {
  stopDrawerStatusPoll();
  statusPollInterval = setInterval(async () => {
    const status = await API.get('/api/monitor/status');
    if (!status.active) {
      stopDrawerStatusPoll();

      const startBtn = document.getElementById('btn-start-monitor');
      const stopBtn = document.getElementById('btn-stop-monitor');
      const statusDot = document.getElementById('monitor-status-dot');
      const statusText = document.getElementById('monitor-status-text');
      const statusDetail = document.getElementById('monitor-status-detail');

      statusDot.className = 'w-3 h-3 rounded-full bg-error';
      statusText.textContent = 'Finished';
      statusDetail.textContent = status.lastAction === 'expired' ? 'Timer expired — app killed.' : 'App opened — app killed.';

      stopBtn.classList.add('hidden');
      startBtn.classList.remove('hidden');
      startBtn.disabled = false;

      renderDashboard(); // Re-render grid stats
      return;
    }
    if (status.appId !== id) return;
    setDrawerMonitoringActive(status);
  }, 2000);
}

function stopDrawerStatusPoll() {
  if (statusPollInterval) {
    clearInterval(statusPollInterval);
    statusPollInterval = null;
  }
}

// --- ADB Connect Form Handler ---
async function handleAdbConnect(e) {
  e.preventDefault();
  const addr = document.getElementById('input-adb-address').value.trim();
  const pin = document.getElementById('input-pin-gate').value || '';

  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  btn.textContent = 'Connecting...';

  // API put to settings will trigger connect
  const res = await fetch('/api/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-Password': pin },
    body: JSON.stringify({ adbAddress: addr }),
  });
  const result = await res.json();
  btn.disabled = false;
  btn.innerHTML = '<span class="material-symbols-outlined text-[20px]">cable</span>Connect Device';

  if (result.error) {
    alert('Connection Failed: ' + result.error);
  } else {
    alert('Device Connected successfully!');
    location.hash = '#dashboard';
  }
}

// --- Global Settings Page / PIN Gate ---
async function renderGlobalSettings() {
  const gate = document.getElementById('settings-pin-gate');
  const content = document.getElementById('settings-content');
  const gateError = document.getElementById('pin-gate-error');

  gate.classList.remove('hidden');
  content.classList.add('hidden');
  gateError.classList.add('hidden');
  document.getElementById('input-pin-gate').value = '';
  document.getElementById('input-new-pin').value = '';

  const check = await API.get('/api/settings/has-password');

  if (!check.hasPassword) {
    // No password set — unlock settings page immediately
    gate.classList.add('hidden');
    content.classList.remove('hidden');
    return;
  }

  // Gate binding
  document.getElementById('btn-unlock-settings').onclick = async () => {
    const pin = document.getElementById('input-pin-gate').value;
    const result = await API.post('/api/settings/verify', { password: pin });
    if (result.valid) {
      gate.classList.add('hidden');
      content.classList.remove('hidden');
      gateError.classList.add('hidden');
      // Set value in Connect form Address field if exists
      const res = await fetch('/api/settings', { headers: { 'X-Password': pin } });
      if (res.ok) {
        const settings = await res.json();
        if (settings.adbAddress) {
          document.getElementById('input-adb-address').value = settings.adbAddress;
        }
      }
    } else {
      gateError.classList.remove('hidden');
    }
  };

  document.getElementById('input-pin-gate').addEventListener('input', (e) => {
    if (e.target.value.length === 6) {
      document.getElementById('btn-unlock-settings').click();
    }
  });
}

async function saveGlobalSettings(e) {
  e.preventDefault();
  const newPin = document.getElementById('input-new-pin').value.trim();
  const pin = document.getElementById('input-pin-gate').value || '';

  if (newPin && !/^\d{6}$/.test(newPin)) {
    return alert('PIN must be exactly 6 digits.');
  }

  const body = {};
  if (newPin) body.password = newPin;

  const res = await fetch('/api/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-Password': pin },
    body: JSON.stringify(body),
  });
  const result = await res.json();
  if (result.error) {
    alert('Error saving PIN: ' + result.error);
  } else {
    alert('PIN updated successfully!');
    location.hash = '#dashboard';
  }
}

// --- App Modal (Add / Edit) ---
function openAddModal() {
  document.getElementById('modal-title').textContent = 'Add App';
  document.getElementById('input-app-id').value = '';
  document.getElementById('input-app-name').value = '';
  document.getElementById('input-package-name').value = '';
  document.getElementById('app-modal').classList.remove('hidden');
}

function openEditModal(id, name, packageName) {
  document.getElementById('modal-title').textContent = 'Edit App';
  document.getElementById('input-app-id').value = id;
  document.getElementById('input-app-name').value = name;
  document.getElementById('input-package-name').value = packageName;
  document.getElementById('app-modal').classList.remove('hidden');
}

function closeModal() {
  document.getElementById('app-modal').classList.add('hidden');
}

async function saveApp(e) {
  e.preventDefault();
  const id = document.getElementById('input-app-id').value;
  const name = document.getElementById('input-app-name').value.trim();
  const packageName = document.getElementById('input-package-name').value.trim();

  let result;
  if (id) {
    result = await API.put(`/api/apps/${id}`, { name, packageName });
  } else {
    result = await API.post('/api/apps', { name, packageName });
  }

  if (result.error) {
    alert('Error: ' + result.error);
    return;
  }

  closeModal();
  renderDashboard();
}

// --- Utility ---
function escHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// --- Init ---
document.addEventListener('DOMContentLoaded', () => {
  // SPA Router
  window.addEventListener('hashchange', router);
  router(); // trigger initial route

  // Form bindings
  document.getElementById('form-app').addEventListener('submit', saveApp);
  document.getElementById('form-settings').addEventListener('submit', saveGlobalSettings);
  document.getElementById('form-adb-connect').addEventListener('submit', handleAdbConnect);

  // App Modal cancel
  document.getElementById('btn-cancel-modal').addEventListener('click', closeModal);
  document.getElementById('app-modal').addEventListener('click', (e) => {
    if (e.target === document.getElementById('app-modal')) closeModal();
  });

  // App Drawer bindings
  document.getElementById('btn-close-drawer').addEventListener('click', closeAppMonitor);
  document.getElementById('drawer-backdrop').addEventListener('click', closeAppMonitor);

  // Trigger Add App triggers
  document.getElementById('btn-add-app-header').addEventListener('click', openAddModal);

  const mobileAddBtn = document.getElementById('mobile-add-btn');
  if (mobileAddBtn) mobileAddBtn.addEventListener('click', openAddModal);

  // Mobile Menu toggles
  const mobileBtn = document.getElementById('mobile-menu-btn');
  if (mobileBtn) {
    mobileBtn.addEventListener('click', () => {
      const aside = document.querySelector('aside');
      aside.classList.toggle('hidden');
    });
  }

  // Auto-hide mobile aside when clicking a link
  document.querySelectorAll('aside nav a').forEach(a => {
    a.addEventListener('click', () => {
      if (window.innerWidth < 768) {
        document.querySelector('aside').classList.add('hidden');
      }
    });
  });

  // Micro-interactions layer
  document.querySelectorAll('.glass-card').forEach(card => {
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      card.style.setProperty('--mouse-x', `${x}px`);
      card.style.setProperty('--mouse-y', `${y}px`);
    });
  });
});
