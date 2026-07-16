const { execFile, exec } = require('child_process');

const PKG_RE = /^[a-zA-Z0-9._-]+$/;
const ADDR_RE = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d{2,5}$/;

function run(cmd, args) {
  return new Promise((resolve, reject) => {
    execFile(cmd, args, { timeout: 10000 }, (err, stdout, stderr) => {
      if (err) return reject(new Error(stderr || err.message));
      resolve(stdout.trim());
    });
  });
}

function runShell(command) {
  return new Promise((resolve, reject) => {
    exec(command, { timeout: 10000 }, (err, stdout, stderr) => {
      if (err) return reject(new Error(stderr || err.message));
      resolve(stdout.trim());
    });
  });
}

function validatePackage(pkg) {
  if (!PKG_RE.test(pkg)) throw new Error('Invalid package name');
}

function validateAddress(addr) {
  if (!ADDR_RE.test(addr)) throw new Error('Invalid ADB address');
}

async function connect(addr) {
  validateAddress(addr);
  return run('adb', ['connect', addr]);
}

async function forceStop(pkg) {
  validatePackage(pkg);
  return run('adb', ['shell', 'am', 'force-stop', pkg]);
}

async function getForegroundApp() {
  // Try mResumedActivity first (Android 10+), fall back to mFocusedActivity
  try {
    const out = await runShell("adb shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|mFocusedActivity' | head -1");
    // Format: mResumedActivity: ActivityRecord{... com.whatsapp/.MainActivity ...}
    const match = out.match(/\s([a-zA-Z0-9._-]+)\//);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}

module.exports = { connect, forceStop, getForegroundApp, validatePackage, validateAddress };
