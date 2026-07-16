const express = require('express');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const adb = require('./adb');
const monitor = require('./monitor');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const DB_PATH = path.join(__dirname, 'db.json');

// SHA-256 for password hashing
function hashPassword(pw) {
  return crypto.createHash('sha256').update(pw).digest('hex');
}

function loadDb() {
  if (!fs.existsSync(DB_PATH)) {
    const init = { apps: [], global: { adbAddress: null, passwordHash: null } };
    fs.writeFileSync(DB_PATH, JSON.stringify(init, null, 2));
    return init;
  }
  try {
    return JSON.parse(fs.readFileSync(DB_PATH, 'utf8'));
  } catch (e) {
    return { apps: [], global: { adbAddress: null, passwordHash: null } };
  }
}

function saveDb(data) {
  fs.writeFileSync(DB_PATH, JSON.stringify(data, null, 2));
}

// Global initialization of ADB connection if settings exist
const db = loadDb();
if (db.global.adbAddress) {
  console.log(`[init] Reconnecting to ADB at: ${db.global.adbAddress}`);
  adb.connect(db.global.adbAddress)
    .then(() => console.log('[init] ADB Connected'))
    .catch(err => console.error('[init] ADB Connection failed:', err.message));
}

// Password auth middleware for global settings
function authMiddleware(req, res, next) {
  const db = loadDb();
  // If no password set yet, bypass auth so they can set it up
  if (!db.global.passwordHash) return next();

  const clientPass = req.headers['x-password'];
  if (!clientPass || hashPassword(clientPass) !== db.global.passwordHash) {
    return res.status(401).json({ error: 'Unauthorized. Invalid PIN.' });
  }
  next();
}

// --- API ROUTES ---

// 1. App CRUD
app.get('/api/apps', (req, res) => {
  const db = loadDb();
  res.json({ apps: db.apps });
});

app.post('/api/apps', (req, res) => {
  const { name, packageName } = req.body;
  if (!name || !packageName) return res.status(400).json({ error: 'Missing name or package' });
  try {
    adb.validatePackage(packageName);
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }

  const db = loadDb();
  const id = crypto.randomBytes(4).toString('hex');
  const newApp = { id, name, packageName, durationMin: 30 }; // Default 30 mins
  db.apps.push(newApp);
  saveDb(db);

  res.status(201).json({ app: newApp });
});

app.put('/api/apps/:id', (req, res) => {
  const { id } = req.params;
  const { name, packageName, durationMin } = req.body;

  const db = loadDb();
  const appIndex = db.apps.findIndex(a => a.id === id);
  if (appIndex === -1) return res.status(404).json({ error: 'App not found' });

  if (packageName) {
    try {
      adb.validatePackage(packageName);
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }
    db.apps[appIndex].packageName = packageName;
  }
  if (name) db.apps[appIndex].name = name;
  if (durationMin !== undefined) {
    const dur = parseInt(durationMin, 10);
    if (isNaN(dur) || dur < 1 || dur > 1440) return res.status(400).json({ error: 'Invalid duration (1-1440 min)' });
    db.apps[appIndex].durationMin = dur;
  }

  saveDb(db);
  res.json({ app: db.apps[appIndex] });
});

app.delete('/api/apps/:id', (req, res) => {
  const { id } = req.params;
  const db = loadDb();
  const appIndex = db.apps.findIndex(a => a.id === id);
  if (appIndex === -1) return res.status(404).json({ error: 'App not found' });

  const activeStatus = monitor.status();
  if (activeStatus.active && activeStatus.appId === id) {
    monitor.stop();
  }

  db.apps.splice(appIndex, 1);
  saveDb(db);
  res.json({ ok: true });
});

// 2. Monitoring
app.post('/api/monitor/start', (req, res) => {
  const { appId, durationMin } = req.body;
  if (!appId || !durationMin) return res.status(400).json({ error: 'Missing appId or durationMin' });

  const db = loadDb();
  const appItem = db.apps.find(a => a.id === appId);
  if (!appItem) return res.status(404).json({ error: 'App not found' });

  // Update saved app duration for future runs
  appItem.durationMin = durationMin;
  saveDb(db);

  if (!db.global.adbAddress) return res.status(400).json({ error: 'ADB connection address not configured in Global Settings' });

  // Ensure adb is connected
  adb.connect(db.global.adbAddress)
    .then(() => {
      monitor.start(appId, appItem.packageName, durationMin);
      res.json({ ok: true });
    })
    .catch(err => {
      res.status(500).json({ error: `ADB Connection failed: ${err.message}` });
    });
});

app.post('/api/monitor/stop', (req, res) => {
  monitor.stop();
  res.json({ ok: true });
});

app.get('/api/monitor/status', (req, res) => {
  res.json(monitor.status());
});

// 3. Settings (locked with passwordHash)
app.get('/api/settings', authMiddleware, (req, res) => {
  const db = loadDb();
  res.json({ adbAddress: db.global.adbAddress });
});

app.put('/api/settings', authMiddleware, async (req, res) => {
  const { adbAddress, password } = req.body;
  const db = loadDb();

  // If setting address
  if (adbAddress) {
    try {
      adb.validateAddress(adbAddress);
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }

    try {
      await adb.connect(adbAddress);
      db.global.adbAddress = adbAddress;
    } catch (err) {
      return res.status(500).json({ error: `Could not connect to ADB: ${err.message}` });
    }
  }

  // If setting/updating password
  if (password) {
    if (!/^\d{6}$/.test(password)) {
      return res.status(400).json({ error: 'Password must be exactly 6 digits' });
    }
    db.global.passwordHash = hashPassword(password);
  }

  saveDb(db);
  res.json({ ok: true });
});

app.post('/api/settings/verify', (req, res) => {
  const { password } = req.body;
  if (!password) return res.status(400).json({ error: 'Missing password' });

  const db = loadDb();
  if (!db.global.passwordHash) {
    // If password is not set yet, PIN is valid by default for setup
    return res.json({ valid: true, setup: true });
  }

  const valid = hashPassword(password) === db.global.passwordHash;
  res.json({ valid });
});

app.get('/api/settings/has-password', (req, res) => {
  const db = loadDb();
  res.json({ hasPassword: !!db.global.passwordHash });
});

// Start Express server
const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server listening on http://0.0.0.0:${PORT}`);
});
