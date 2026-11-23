'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { open } = require('./db');
const { handle } = require('./api');

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '127.0.0.1';
const DASHBOARD = path.resolve(__dirname, '..', 'dashboard');

const db = open();
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.json': 'application/json; charset=utf-8'
};

function serveStatic(req, res, pathname) {
  if (pathname === '/') pathname = '/index.html';
  const target = path.resolve(DASHBOARD, '.' + pathname);
  if (!target.startsWith(DASHBOARD + path.sep) && target !== path.join(DASHBOARD, 'index.html')) {
    res.writeHead(403); res.end('Forbidden'); return;
  }
  fs.readFile(target, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not found'); return; }
    res.writeHead(200, {
      'Content-Type': TYPES[path.extname(target)] || 'application/octet-stream',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'Cache-Control': 'no-cache'
    });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    if (url.pathname.startsWith('/api/')) return await handle(db, req, res);
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end('Method not allowed'); return; }
    return serveStatic(req, res, url.pathname);
  } catch (err) {
    console.error(err);
    if (!res.headersSent) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Internal server error' }));
    } else res.end();
  }
});

server.listen(PORT, HOST, () => {
  console.log(`XMR Miner Pro management server listening on http://${HOST}:${PORT}`);
  console.log(`Database: ${process.env.DB_PATH || path.join(__dirname, 'data', 'xmrminerpro.sqlite')}`);
});

function shutdown() {
  server.close(() => { try { db.close(); } catch {} process.exit(0); });
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
