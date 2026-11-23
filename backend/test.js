'use strict';
// End-to-end API smoke test. Start the server first, then: node backend/test.js
const BASE = process.env.BASE_URL || 'http://127.0.0.1:8787';
let passed = 0;
function check(name, cond, extra = '') {
  if (cond) { passed++; console.log('  PASS ' + name); }
  else { console.error('  FAIL ' + name + (extra ? ' — ' + extra : '')); process.exitCode = 1; }
}
async function call(path, { method = 'GET', token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data = {};
  try { data = await res.json(); } catch {}
  return { status: res.status, data };
}
(async () => {
  console.log(`Testing ${BASE}`);
  const email = `test.${Date.now()}@example.com`;
  const password = 'correct-horse-battery-staple';
  let r = await call('/api/auth/register', { method: 'POST', body: { email, password } });
  check('register returns 201 and token', r.status === 201 && !!r.data.token, JSON.stringify(r));
  const token = r.data.token;

  r = await call('/api/auth/login', { method: 'POST', body: { email, password } });
  const loginUid = r.data.token ? JSON.parse(Buffer.from(r.data.token.split('.')[0], 'base64url').toString()).uid : null;
  check('login succeeds', r.status === 200 && !!r.data.token && typeof loginUid === 'number', JSON.stringify(r.status));

  r = await call('/api/me', { token });
  check('me reports plan and limit', r.status === 200 && r.data.plan === 'free' && r.data.device_limit === 1, JSON.stringify(r.data));

  r = await call('/api/devices', { method: 'POST', token, body: { name: 'Test Phone', platform: 'android' } });
  check('device registration', r.status === 201 && /^[a-f0-9]{16}$/.test(r.data.id) && !!r.data.token, JSON.stringify(r));
  const id = r.data.id;
  const deviceToken = r.data.token;

  r = await call('/api/devices', { method: 'POST', token, body: { name: 'Second', platform: 'linux' } });
  check('free plan limit enforced', r.status === 403, JSON.stringify(r));

  r = await call(`/api/devices/${id}/telemetry`, { method: 'POST', token: deviceToken, body: {
    hashrate: 1234.5, hashrate_avg: 1100, accepted: 7, rejected: 1, uptime_s: 600,
    cpu_percent: 42, temp_c: 61.5, battery_pct: 88, charging: true,
    pool: 'pool.supportxmr.com:3333', status: 'mining'
  }});
  check('telemetry accepted', r.status === 200 && r.data.ok === true, JSON.stringify(r));

  r = await call('/api/devices', { token });
  check('fleet summary reflects telemetry', r.status === 200 && r.data.summary.online === 1 &&
    r.data.summary.combined_hashrate === 1234.5 && r.data.summary.accepted_shares === 7,
    JSON.stringify(r.data.summary));
  check('device marked online', r.data.devices[0]?.online === true);

  r = await call(`/api/devices/${id}`, { token });
  check('history returns the sample', r.status === 200 && r.data.history.length === 1 &&
    r.data.history[0].temp_c === 61.5 && r.data.history[0].charging === true,
    JSON.stringify(r.data.history));

  r = await call(`/api/devices/${id}/rotate`, { method: 'POST', token });
  check('token rotated', r.status === 200 && !!r.data.token && r.data.token !== deviceToken, JSON.stringify(r));
  const newToken = r.data.token;

  r = await call(`/api/devices/${id}/telemetry`, { method: 'POST', token: deviceToken, body: { hashrate: 1 } });
  check('old token rejected', r.status === 401, JSON.stringify(r));

  r = await call(`/api/devices/${id}/telemetry`, { method: 'POST', token: newToken, body: { status: 'stopped', hashrate: 0 } });
  check('new token works', r.status === 200, JSON.stringify(r));

  r = await call(`/api/devices/${id}`, { method: 'DELETE', token });
  check('device removed', r.status === 200 && r.data.deleted === true, JSON.stringify(r));

  r = await call('/api/devices', { token });
  check('fleet empty after removal', r.data.summary.total_devices === 0, JSON.stringify(r.data.summary));

  r = await call('/api/me');
  check('auth required without token', r.status === 401, JSON.stringify(r));

  console.log(`\n${passed} checks passed`);
})().catch(e => { console.error(e); process.exitCode = 1; });
