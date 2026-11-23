'use strict';
(function () {
  const $ = id => document.getElementById(id);
  const TOKEN_KEY = 'xmrminerpro.token';
  let token = localStorage.getItem(TOKEN_KEY) || '';
  let me = null;
  let devices = [];
  let selected = null;
  let mode = 'login';
  let pollTimer = null;

  function showToast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.classList.remove('hidden');
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => t.classList.add('hidden'), 3200);
  }

  async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (options.body) headers['Content-Type'] = 'application/json';
    const res = await fetch(path, { ...options, headers, body: options.body ? JSON.stringify(options.body) : undefined });
    let data = {};
    try { data = await res.json(); } catch {}
    if (!res.ok) {
      if (res.status === 401 && path !== '/api/auth/login') signOut();
      throw new Error(data.error || `Request failed (${res.status})`);
    }
    return data;
  }

  function rate(n) {
    n = Number(n) || 0;
    if (n >= 1e6) return (n / 1e6).toFixed(2) + ' MH/s';
    if (n >= 1e3) return (n / 1e3).toFixed(2) + ' kH/s';
    return n.toFixed(0) + ' H/s';
  }
  function ago(ts) {
    if (!ts) return 'never';
    const s = Math.floor((Date.now() - ts) / 1000);
    if (s < 60) return s + 's ago';
    if (s < 3600) return Math.floor(s / 60) + 'm ago';
    if (s < 86400) return Math.floor(s / 3600) + 'h ago';
    return Math.floor(s / 86400) + 'd ago';
  }
  function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function setAuthUI() {
    if (me) {
      $('authView').classList.add('hidden');
      $('appView').classList.remove('hidden');
      $('userbar').classList.remove('hidden');
      $('userEmail').textContent = me.email;
      $('planBadge').textContent = (me.plan || 'free').toUpperCase();
      startPolling();
    } else {
      $('appView').classList.add('hidden');
      $('authView').classList.remove('hidden');
      $('userbar').classList.add('hidden');
      stopPolling();
    }
  }

  function signOut() {
    token = '';
    me = null;
    devices = [];
    selected = null;
    localStorage.removeItem(TOKEN_KEY);
    setAuthUI();
  }

  function setMode(next) {
    mode = next;
    $('tabLogin').classList.toggle('active', mode === 'login');
    $('tabRegister').classList.toggle('active', mode === 'register');
    $('authSubmit').textContent = mode === 'login' ? 'Sign in' : 'Create account';
    $('authPassword').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    $('authError').textContent = '';
  }

  function renderOverview() {
    const sum = devices.reduce((a, d) => a + (d.latest?.hashrate || 0), 0);
    const shares = devices.reduce((a, d) => a + (d.latest?.accepted || 0), 0);
    $('statDevices').textContent = devices.length;
    $('statLimit').textContent = me ? `of ${me.device_limit} on ${me.plan}` : '';
    $('statOnline').textContent = devices.filter(d => d.online).length;
    $('statHashrate').textContent = rate(sum);
    $('statShares').textContent = shares.toLocaleString();
    $('lastUpdate').textContent = 'updated ' + new Date().toLocaleTimeString();
    if (!devices.length) {
      $('fleetTable').innerHTML = '<p class="muted">No devices yet. Use “Add device” to register one.</p>';
      return;
    }
    $('fleetTable').innerHTML = `<table><thead><tr><th>Device</th><th>Platform</th><th>Status</th><th>Hashrate</th><th>Shares</th><th>Seen</th></tr></thead><tbody>` +
      devices.map(d => `<tr>
        <td>${esc(d.name)}</td>
        <td>${esc(d.platform)}</td>
        <td><span class="pill ${d.online ? 'online' : 'offline'}">${d.online ? 'online' : 'offline'}</span></td>
        <td class="green">${rate(d.latest?.hashrate)}</td>
        <td>${d.latest?.accepted ?? 0}</td>
        <td>${ago(d.last_seen)}</td>
      </tr>`).join('') + '</tbody></table>';
  }

  function renderDevices() {
    $('deviceList').innerHTML = devices.length ? devices.map(d => `
      <div class="device-card" data-id="${d.id}">
        <h3>${esc(d.name)}</h3>
        <span class="pill ${d.online ? 'online' : 'offline'}">${d.online ? 'online' : 'offline'}</span>
        <div class="rate">${rate(d.latest?.hashrate)}</div>
        <p class="muted">${esc(d.platform)} · ${ago(d.last_seen)}</p>
      </div>`).join('') : '<p class="muted">No devices registered.</p>';
    $('deviceList').querySelectorAll('.device-card').forEach(el => {
      el.addEventListener('click', () => openDevice(el.dataset.id));
    });
  }

  function chartSvg(points) {
    if (!points.length) return '<p class="muted">No history yet.</p>';
    const w = 600, h = 140, pad = 8;
    const max = Math.max(1, ...points.map(p => p.hashrate || 0));
    const step = points.length > 1 ? (w - pad * 2) / (points.length - 1) : 0;
    const coords = points.map((p, i) => `${pad + i * step},${h - pad - ((p.hashrate || 0) / max) * (h - pad * 2)}`);
    return `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">
      <polyline fill="none" stroke="#35d07f" stroke-width="2" points="${coords.join(' ')}"/>
    </svg>`;
  }

  async function openDevice(id) {
    selected = id;
    $('deviceMsg').textContent = 'Loading…';
    try {
      const data = await api(`/api/devices/${id}`);
      const d = data.device;
      const t = data.history[data.history.length - 1] || {};
      $('deviceDetail').classList.remove('hidden');
      $('detailName').textContent = d.name;
      $('detailStats').innerHTML = [
        ['Status', t.status || d.status],
        ['Hashrate', rate(t.hashrate)],
        ['Avg hashrate', rate(t.hashrate_avg)],
        ['Accepted', t.accepted ?? 0],
        ['Rejected', t.rejected ?? 0],
        ['Uptime', (t.uptime_s ?? 0) + 's'],
        ['CPU', t.cpu_percent != null ? t.cpu_percent.toFixed(0) + '%' : '—'],
        ['Temp', t.temp_c != null ? t.temp_c.toFixed(1) + '°C' : '—'],
        ['Battery', t.battery_pct != null ? t.battery_pct + '%' + (t.charging ? ' (charging)' : '') : '—'],
        ['Pool', t.pool || '—'],
        ['Last seen', ago(d.last_seen)]
      ].map(([k, v]) => `<div><span>${esc(k)}</span><strong>${esc(v)}</strong></div>`).join('');
      $('chart').innerHTML = chartSvg(data.history);
      $('deviceMsg').textContent = '';
    } catch (e) {
      $('deviceMsg').textContent = e.message;
    }
  }

  async function refresh() {
    if (!me) return;
    try {
      const data = await api('/api/devices');
      devices = data.devices;
      me.plan = data.summary.plan;
      me.device_limit = data.summary.device_limit;
      $('planBadge').textContent = me.plan.toUpperCase();
      renderOverview();
      renderDevices();
      if (selected && !$('deviceDetail').classList.contains('hidden')) await openDevice(selected);
    } catch (e) {
      if (e.message) $('lastUpdate').textContent = e.message;
    }
  }

  function startPolling() {
    stopPolling();
    refresh();
    pollTimer = setInterval(refresh, 5000);
  }
  function stopPolling() { if (pollTimer) clearInterval(pollTimer); pollTimer = null; }

  function switchView(view) {
    document.querySelectorAll('.navbtn').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    ['overview', 'devices', 'add', 'docs'].forEach(v => $('view-' + v).classList.toggle('hidden', v !== view));
    if (view !== 'devices') $('deviceDetail').classList.add('hidden');
  }

  $('tabLogin').addEventListener('click', () => setMode('login'));
  $('tabRegister').addEventListener('click', () => setMode('register'));
  $('authForm').addEventListener('submit', async e => {
    e.preventDefault();
    $('authError').textContent = '';
    $('authSubmit').disabled = true;
    try {
      const data = await api(`/api/auth/${mode === 'login' ? 'login' : 'register'}`, {
        method: 'POST',
        body: { email: $('authEmail').value, password: $('authPassword').value }
      });
      token = data.token;
      localStorage.setItem(TOKEN_KEY, token);
      me = { email: data.email, plan: data.plan, device_limit: null };
      $('authPassword').value = '';
      setAuthUI();
      showToast(mode === 'login' ? 'Signed in' : 'Account created');
    } catch (err) {
      $('authError').textContent = err.message;
    } finally {
      $('authSubmit').disabled = false;
    }
  });
  $('logoutBtn').addEventListener('click', () => { signOut(); showToast('Signed out'); });
  document.querySelectorAll('.navbtn').forEach(b => b.addEventListener('click', () => switchView(b.dataset.view)));
  $('closeDetail').addEventListener('click', () => { selected = null; $('deviceDetail').classList.add('hidden'); });

  $('addForm').addEventListener('submit', async e => {
    e.preventDefault();
    try {
      const d = await api('/api/devices', {
        method: 'POST',
        body: { name: $('devName').value, platform: $('devPlatform').value }
      });
      $('newDevice').innerHTML = `<strong>Device registered.</strong> Copy these now — the token is shown only once.<br>
        ID: <code>${esc(d.id)}</code><br>Token: <code>${esc(d.token)}</code>`;
      $('newDevice').classList.remove('hidden');
      $('devName').value = '';
      refresh();
    } catch (err) { showToast(err.message); }
  });

  $('rotateBtn').addEventListener('click', async () => {
    if (!selected || !window.confirm('Rotate this device token? The old token stops working immediately.')) return;
    try {
      const d = await api(`/api/devices/${selected}/rotate`, { method: 'POST' });
      $('deviceMsg').innerHTML = `New token (copy now): <code>${esc(d.token)}</code>`;
    } catch (err) { $('deviceMsg').textContent = err.message; }
  });

  $('removeBtn').addEventListener('click', async () => {
    if (!selected || !window.confirm('Remove this device from your account?')) return;
    try {
      await api(`/api/devices/${selected}`, { method: 'DELETE' });
      selected = null;
      $('deviceDetail').classList.add('hidden');
      showToast('Device removed');
      refresh();
    } catch (err) { $('deviceMsg').textContent = err.message; }
  });

  if (token) {
    api('/api/me').then(u => { me = u; setAuthUI(); }).catch(() => signOut());
  } else setAuthUI();
})();
