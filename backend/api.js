'use strict';
const crypto = require('node:crypto');
const { deviceLimit } = require('./db');
const auth = require('./auth');

const ALLOWED_PLATFORMS = new Set(['android', 'windows', 'linux', 'macos', 'other']);
const rate = new Map();

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(data),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  });
  res.end(data);
}

function fail(res, status, message) { json(res, status, { error: message }); }

function readBody(req, max = 16384) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', c => {
      size += c.length;
      if (size > max) { reject(new Error('body too large')); req.destroy(); }
      else chunks.push(c);
    });
    req.on('end', () => {
      try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); }
      catch { reject(new Error('invalid JSON')); }
    });
    req.on('error', reject);
  });
}

function bearer(req) {
  const h = req.headers.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(h);
  return m ? m[1].trim() : null;
}

function userFrom(db, req) {
  const token = bearer(req);
  const payload = auth.verify(token);
  if (!payload || payload.kind !== 'user') return null;
  const row = db.prepare('SELECT id, email, plan, created_at FROM users WHERE id = ?').get(payload.uid);
  return row || null;
}

function deviceFrom(db, req, deviceId) {
  const token = bearer(req);
  if (!token) return null;
  const row = db.prepare('SELECT * FROM devices WHERE id = ? AND token_hash = ?')
    .get(deviceId, auth.tokenHash(token));
  return row || null;
}

function limited(req, res) {
  const ip = req.socket.remoteAddress || 'unknown';
  const now = Date.now();
  const entry = rate.get(ip);
  if (!entry || now > entry.reset) {
    rate.set(ip, { count: 1, reset: now + 10 * 60 * 1000 });
    return false;
  }
  entry.count++;
  return entry.count > 25;
}

function deviceView(row) {
  return {
    id: row.id,
    name: row.name,
    platform: row.platform,
    status: row.status,
    created_at: row.created_at,
    last_seen: row.last_seen
  };
}

function telemetryView(row) {
  return {
    ts: row.ts,
    hashrate: row.hashrate,
    hashrate_avg: row.hashrate_avg,
    accepted: row.accepted,
    rejected: row.rejected,
    uptime_s: row.uptime_s,
    cpu_percent: row.cpu_percent,
    temp_c: row.temp_c,
    battery_pct: row.battery_pct,
    charging: !!row.charging,
    pool: row.pool,
    status: row.status
  };
}

function num(value, fallback, min, max) {
  if (value === undefined || value === null || value === '') return fallback;
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

async function handle(db, req, res) {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;
  const method = req.method;

  if (p.startsWith('/api/auth/') && limited(req, res)) return fail(res, 429, 'Too many attempts, try again later');

  if (p === '/api/auth/register' && method === 'POST') {
    const b = await readBody(req);
    const email = String(b.email || '').trim().toLowerCase();
    const password = String(b.password || '');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email) || email.length > 254) return fail(res, 400, 'Valid email required');
    if (password.length < 10 || password.length > 200) return fail(res, 400, 'Password must be 10–200 characters');
    if (db.prepare('SELECT 1 FROM users WHERE email = ?').get(email)) return fail(res, 409, 'Email already registered');
    const plan = ['free', 'pro', 'business'].includes(b.plan) ? b.plan : 'free';
    const info = db.prepare('INSERT INTO users (email, password_hash, plan, created_at) VALUES (?, ?, ?, ?)')
      .run(email, auth.hashPassword(password), plan, Date.now());
    return json(res, 201, { token: auth.userToken(Number(info.lastInsertRowid)), email, plan });
  }

  if (p === '/api/auth/login' && method === 'POST') {
    const b = await readBody(req);
    const email = String(b.email || '').trim().toLowerCase();
    const row = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
    if (!row || !auth.verifyPassword(String(b.password || ''), row.password_hash)) {
      return fail(res, 401, 'Invalid email or password');
    }
    return json(res, 200, { token: auth.userToken(row.id), email: row.email, plan: row.plan });
  }

  if (p === '/api/me' && method === 'GET') {
    const user = userFrom(db, req);
    if (!user) return fail(res, 401, 'Sign in required');
    return json(res, 200, { ...user, device_limit: deviceLimit(user.plan) });
  }

  if (p === '/api/devices' && method === 'POST') {
    const user = userFrom(db, req);
    if (!user) return fail(res, 401, 'Sign in required');
    const b = await readBody(req);
    const name = String(b.name || '').trim();
    const platform = String(b.platform || '').trim().toLowerCase();
    if (name.length < 1 || name.length > 60) return fail(res, 400, 'Name must be 1–60 characters');
    if (!ALLOWED_PLATFORMS.has(platform)) return fail(res, 400, 'Unsupported platform');
    const count = db.prepare('SELECT COUNT(*) AS n FROM devices WHERE user_id = ?').get(user.id).n;
    if (count >= deviceLimit(user.plan)) return fail(res, 403, `Your ${user.plan} plan allows ${deviceLimit(user.plan)} device(s)`);
    const id = crypto.randomBytes(8).toString('hex');
    const token = auth.deviceToken();
    db.prepare(`INSERT INTO devices (id, user_id, name, platform, token_hash, created_at)
                VALUES (?, ?, ?, ?, ?, ?)`).run(id, user.id, name, platform, auth.tokenHash(token), Date.now());
    return json(res, 201, { id, name, platform, token });
  }

  if (p === '/api/devices' && method === 'GET') {
    const user = userFrom(db, req);
    if (!user) return fail(res, 401, 'Sign in required');
    const rows = db.prepare('SELECT * FROM devices WHERE user_id = ? ORDER BY created_at DESC').all(user.id);
    const devices = rows.map(row => {
      const last = db.prepare('SELECT * FROM telemetry WHERE device_id = ? ORDER BY ts DESC LIMIT 1').get(row.id);
      return {
        ...deviceView(row),
        latest: last ? telemetryView(last) : null,
        online: last ? (Date.now() - last.ts) < 180000 && last.status !== 'stopped' : false
      };
    });
    const combined = devices.reduce((sum, d) => sum + (d.latest?.hashrate || 0), 0);
    const accepted = devices.reduce((sum, d) => sum + (d.latest?.accepted || 0), 0);
    return json(res, 200, {
      devices,
      summary: {
        total_devices: devices.length,
        online: devices.filter(d => d.online).length,
        combined_hashrate: combined,
        accepted_shares: accepted,
        device_limit: deviceLimit(user.plan),
        plan: user.plan
      }
    });
  }

  let m = /^\/api\/devices\/([a-f0-9]{16})$/.exec(p);
  if (m) {
    const user = userFrom(db, req);
    if (!user) return fail(res, 401, 'Sign in required');
    const id = m[1];
    const row = db.prepare('SELECT * FROM devices WHERE id = ? AND user_id = ?').get(id, user.id);
    if (!row) return fail(res, 404, 'Device not found');
    if (method === 'GET') {
      const history = db.prepare('SELECT * FROM telemetry WHERE device_id = ? ORDER BY ts DESC LIMIT 120')
        .all(id).map(telemetryView).reverse();
      return json(res, 200, { device: deviceView(row), history });
    }
    if (method === 'DELETE') {
      db.prepare('DELETE FROM devices WHERE id = ? AND user_id = ?').run(id, user.id);
      return json(res, 200, { deleted: true });
    }
  }

  m = /^\/api\/devices\/([a-f0-9]{16})\/rotate$/.exec(p);
  if (m && method === 'POST') {
    const user = userFrom(db, req);
    if (!user) return fail(res, 401, 'Sign in required');
    const row = db.prepare('SELECT * FROM devices WHERE id = ? AND user_id = ?').get(m[1], user.id);
    if (!row) return fail(res, 404, 'Device not found');
    const token = auth.deviceToken();
    db.prepare('UPDATE devices SET token_hash = ? WHERE id = ?').run(auth.tokenHash(token), m[1]);
    return json(res, 200, { token });
  }

  m = /^\/api\/devices\/([a-f0-9]{16})\/telemetry$/.exec(p);
  if (m && method === 'POST') {
    const row = deviceFrom(db, req, m[1]);
    if (!row) return fail(res, 401, 'Invalid device token');
    const b = await readBody(req);
    const ts = Date.now();
    db.prepare(`INSERT INTO telemetry
      (device_id, ts, hashrate, hashrate_avg, accepted, rejected, uptime_s, cpu_percent, temp_c, battery_pct, charging, pool, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`).run(
      m[1], ts,
      num(b.hashrate, 0, 0, 1e9),
      num(b.hashrate_avg, 0, 0, 1e9),
      Math.floor(num(b.accepted, 0, 0, 1e9)),
      Math.floor(num(b.rejected, 0, 0, 1e9)),
      Math.floor(num(b.uptime_s, 0, 0, 1e9)),
      num(b.cpu_percent, 0, 0, 100),
      num(b.temp_c, null, -50, 150),
      Math.floor(num(b.battery_pct, null, 0, 100)),
      b.charging ? 1 : 0,
      String(b.pool || '').slice(0, 120),
      String(b.status || 'mining').slice(0, 40)
    );
    db.prepare('UPDATE devices SET last_seen = ?, status = ? WHERE id = ?').run(ts, String(b.status || 'mining').slice(0, 40), m[1]);
    return json(res, 200, { ok: true });
  }

  return fail(res, 404, 'Not found');
}

module.exports = { handle };
