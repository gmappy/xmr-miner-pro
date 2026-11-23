'use strict';
const { DatabaseSync } = require('node:sqlite');
const path = require('node:path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'data', 'xmrminerpro.sqlite');

function open() {
  const db = new DatabaseSync(DB_PATH);
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS users (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      email         TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      plan          TEXT NOT NULL DEFAULT 'free',
      created_at    INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS devices (
      id          TEXT PRIMARY KEY,
      user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      name        TEXT NOT NULL,
      platform    TEXT NOT NULL,
      token_hash  TEXT NOT NULL,
      created_at  INTEGER NOT NULL,
      last_seen   INTEGER,
      status      TEXT NOT NULL DEFAULT 'offline'
    );

    CREATE TABLE IF NOT EXISTS telemetry (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id     TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
      ts            INTEGER NOT NULL,
      hashrate      REAL,
      hashrate_avg  REAL,
      accepted      INTEGER,
      rejected      INTEGER,
      uptime_s      INTEGER,
      cpu_percent   REAL,
      temp_c        REAL,
      battery_pct   INTEGER,
      charging      INTEGER,
      pool          TEXT,
      status        TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_telemetry_device_ts ON telemetry(device_id, ts DESC);
    CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
  `);
  return db;
}

const PLAN_LIMITS = { free: 1, pro: 10, business: 100 };
function deviceLimit(plan) { return PLAN_LIMITS[plan] || PLAN_LIMITS.free; }

module.exports = { open, deviceLimit, PLAN_LIMITS };
