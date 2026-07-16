const adb = require('./adb');

const state = {
  active: false,
  appId: null,
  packageName: null,
  timeoutAt: null,
  intervalId: null,
  lastAction: null, // 'force-stopped' | 'expired' | null
};

function start(appId, packageName, durationMin) {
  stop(); // clear any existing session
  state.active = true;
  state.appId = appId;
  state.packageName = packageName;
  state.timeoutAt = Date.now() + durationMin * 60000;
  state.lastAction = null;
  state.intervalId = setInterval(poll, 2000);
  console.log(`[monitor] Started watching ${packageName} for ${durationMin}m`);
}

function stop() {
  if (state.intervalId) clearInterval(state.intervalId);
  state.active = false;
  state.appId = null;
  state.packageName = null;
  state.timeoutAt = null;
  state.intervalId = null;
}

async function poll() {
  if (!state.active) return;

  // Timer expired → force-stop
  if (Date.now() >= state.timeoutAt) {
    console.log(`[monitor] Timer expired for ${state.packageName}, force-stopping`);
    try { await adb.forceStop(state.packageName); } catch (e) { console.error('[monitor]', e.message); }
    state.lastAction = 'expired';
    stop();
    return;
  }

  // Check if app is in foreground → force-stop
  const fg = await adb.getForegroundApp();
  if (fg === state.packageName) {
    console.log(`[monitor] ${state.packageName} detected in foreground, force-stopping`);
    try { await adb.forceStop(state.packageName); } catch (e) { console.error('[monitor]', e.message); }
    state.lastAction = 'force-stopped';
    stop();
    return;
  }
}

function status() {
  if (!state.active) return { active: false, lastAction: state.lastAction };
  return {
    active: true,
    appId: state.appId,
    packageName: state.packageName,
    remainingSec: Math.max(0, Math.round((state.timeoutAt - Date.now()) / 1000)),
  };
}

module.exports = { start, stop, status };
