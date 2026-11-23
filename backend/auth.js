'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const SECRET_FILE = process.env.SECRET_FILE || path.join(__dirname, 'data', 'secret.key');

function secret() {
  if (process.env.AUTH_SECRET) return Buffer.from(process.env.AUTH_SECRET, 'utf8');
  try {
    return fs.readFileSync(SECRET_FILE);
  } catch {
    fs.mkdirSync(path.dirname(SECRET_FILE), { recursive: true });
    const key = crypto.randomBytes(48);
    fs.writeFileSync(SECRET_FILE, key, { mode: 0o600 });
    return key;
  }
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const derived = crypto.scryptSync(password, salt, 32, { N: 16384, r: 8, p: 1 });
  return `scrypt$${salt.toString('base64url')}$${derived.toString('base64url')}`;
}

function verifyPassword(password, stored) {
  try {
    const [scheme, saltB64, hashB64] = String(stored).split('$');
    if (scheme !== 'scrypt') return false;
    const salt = Buffer.from(saltB64, 'base64url');
    const expected = Buffer.from(hashB64, 'base64url');
    const actual = crypto.scryptSync(password, salt, expected.length, { N: 16384, r: 8, p: 1 });
    return crypto.timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}

function sign(payload) {
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const mac = crypto.createHmac('sha256', secret()).update(body).digest('base64url');
  return `${body}.${mac}`;
}

function verify(token) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  const expected = crypto.createHmac('sha256', secret()).update(parts[0]).digest();
  let given;
  try { given = Buffer.from(parts[1], 'base64url'); } catch { return null; }
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[0], 'base64url').toString('utf8'));
    if (!payload.exp || payload.exp < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

function userToken(userId) {
  return sign({ uid: userId, kind: 'user', exp: Date.now() + 7 * 24 * 3600 * 1000 });
}

function deviceToken() {
  return crypto.randomBytes(32).toString('base64url');
}

function tokenHash(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

module.exports = {
  hashPassword, verifyPassword, userToken, verify,
  deviceToken, tokenHash
};
