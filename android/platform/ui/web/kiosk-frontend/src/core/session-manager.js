import { config } from './config.js';
import { getCurrentScreen, onScreenChange, showScreen } from './navigation.js';
import { appendSessionEvent, hasSupabaseSessionBridge, syncKioskSessionState } from '@services/supabase-sessions.js';

/** @typedef {import('../types/global.d.ts').SessionState} SessionState */

const SESSION_STORAGE_KEY = 'sessionState';
const SUPABASE_SYNC_DEBOUNCE_MS = 800;
const SYNC_REASON_PRIORITY = {
  hydrate: 0,
  state: 1,
  'session-id': 1,
  'stage-change': 2,
  reset: 3,
};

/**
 * @typedef {{ reason?: keyof typeof SYNC_REASON_PRIORITY; skipSupabase?: boolean }} PersistOptions
 */

/**
 * @typedef {{ reason?: keyof typeof SYNC_REASON_PRIORITY; forceSync?: boolean }} StageUpdateOptions
 */

/** @type {SessionState & Record<string, unknown>} */
const sessionState = {
  contact: {
    thickness: null,
    diagnostics: null,
  },
  session: {
    kiosk: null,
    thicknessId: null,
    obdId: null,
  },
  reportSent: {
    thickness: false,
    diagnostics: false,
  },
  selectedService: null,
  thicknessType: null,
  obdMode: 'general',
  obdMake: null,
  stage: null,
  status: 'created',
  serviceType: null,
};

/** @type {ReturnType<typeof setTimeout> | null} */
let idleTimer = null;
/** @type {Set<(state: SessionState) => void>} */
const sessionListeners = new Set();
/** @type {ReturnType<typeof setTimeout> | null} */
let supabaseSyncTimer = null;
/** @type {keyof typeof SYNC_REASON_PRIORITY | null} */
let pendingSupabaseReason = null;

/**
 * @returns {SessionState}
 */
export function getSessionState() {
  return sessionState;
}

function cloneSessionState() {
  try {
    if (typeof structuredClone === 'function') {
      return structuredClone(sessionState);
    }
  } catch (error) {
    console.warn('[session] structuredClone failed, falling back to JSON', error);
  }
  try {
    return JSON.parse(JSON.stringify(sessionState));
  } catch (error) {
    console.warn('[session] JSON clone failed, falling back to reference', error);
    return sessionState;
  }
}

function publishSessionState() {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.__kioskSessionState = cloneSessionState();
  } catch {
    window.__kioskSessionState = sessionState;
  }
  window.SESSION_ID = sessionState.session.kiosk;
  window.THICKNESS_SESSION_ID = sessionState.session.thicknessId;
  window.OBD_SESSION_ID = sessionState.session.obdId;
}

function notifySessionListeners() {
  sessionListeners.forEach(listener => {
    try {
      listener(sessionState);
    } catch (error) {
      console.error('[session] Listener error:', error);
    }
  });
}

/**
 * @param {PersistOptions} [options]
 */
function persistSessionState(options) {
  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(sessionState));
    } catch (error) {
      console.warn('[session] Failed to persist session state:', error);
    }
  }
  publishSessionState();
  notifySessionListeners();
  if (!options?.skipSupabase) {
    queueSupabaseSync(options?.reason ?? 'state');
  }
}

function shouldOverrideReason(nextReason) {
  if (!pendingSupabaseReason) {
    return true;
  }
  const currentPriority = SYNC_REASON_PRIORITY[pendingSupabaseReason] ?? 1;
  const nextPriority = SYNC_REASON_PRIORITY[nextReason] ?? 1;
  return nextPriority >= currentPriority;
}

/**
 * @param {keyof typeof SYNC_REASON_PRIORITY} [reason]
 */
function queueSupabaseSync(reason = 'state') {
  if (!hasSupabaseSessionBridge()) {
    return;
  }
  const resolvedReason = /** @type {keyof typeof SYNC_REASON_PRIORITY} */ (reason);
  if (shouldOverrideReason(resolvedReason)) {
    pendingSupabaseReason = resolvedReason;
  }
  if (supabaseSyncTimer) {
    clearTimeout(supabaseSyncTimer);
  }
  supabaseSyncTimer = setTimeout(() => {
    supabaseSyncTimer = null;
    void flushSupabaseSync();
  }, SUPABASE_SYNC_DEBOUNCE_MS);
}

function ensureKioskSessionId() {
  if (!sessionState.session.kiosk) {
    sessionState.session.kiosk = generateSessionId('kiosk');
    persistSessionState({ reason: 'session-id' });
  }
  return sessionState.session.kiosk;
}

function deriveStatusFromStage(stage) {
  const normalized = (stage || '').toLowerCase();
  if (!normalized || normalized.includes('attract')) {
    return 'created';
  }
  if (normalized.includes('await') || normalized.includes('qr') || normalized.includes('payment')) {
    return 'awaiting_payment';
  }
  if (normalized.includes('thk') && (normalized.includes('measure') || normalized.includes('instr'))) {
    return 'measuring';
  }
  if (normalized.includes('obd') || normalized.includes('diag') || normalized.includes('scan')) {
    return 'scanning';
  }
  if (normalized.includes('report') || normalized.includes('done') || normalized.includes('result')) {
    return 'reporting';
  }
  if (normalized.includes('completed') || normalized.includes('finish')) {
    return 'completed';
  }
  return 'in_progress';
}

function deriveServiceTypeFromStage(stage) {
  const normalized = (stage || '').toLowerCase();
  if (normalized.includes('obd') || normalized.includes('diag')) {
    return 'diagnostics';
  }
  if (normalized.includes('thk') || normalized.includes('thickness')) {
    return 'thickness';
  }
  return sessionState.selectedService || sessionState.serviceType || null;
}

/**
 * @param {string | null} screenId
 * @param {StageUpdateOptions} [options]
 */
function applyStageFromScreen(screenId, options) {
  const nextStage = screenId || null;
  let changed = false;

  if (sessionState.stage !== nextStage) {
    sessionState.stage = nextStage;
    changed = true;
  }

  const derivedStatus = deriveStatusFromStage(nextStage);
  if (sessionState.status !== derivedStatus) {
    sessionState.status = derivedStatus;
    changed = true;
  }

  const derivedServiceType = deriveServiceTypeFromStage(nextStage);
  if (derivedServiceType && sessionState.serviceType !== derivedServiceType) {
    sessionState.serviceType = derivedServiceType;
    changed = true;
  }

  if (changed) {
    persistSessionState({ reason: options?.reason ?? 'stage-change' });
  } else if (options?.forceSync) {
    queueSupabaseSync(options.reason ?? 'stage-change');
  }
}

function initStageTracking() {
  const initialScreen = getCurrentScreen ? getCurrentScreen() : null;
  if (initialScreen) {
    applyStageFromScreen(initialScreen, { reason: 'hydrate' });
  }
  onScreenChange(screenId => {
    applyStageFromScreen(screenId, { reason: 'stage-change' });
  });
}

/**
 * @param {string} sessionId
 * @param {string | null} stage
 * @param {string} status
 * @param {string | null} serviceType
 */
function recordStageEvent(sessionId, stage, status, serviceType) {
  void appendSessionEvent({
    sessionId,
    eventType: 'ui_stage_changed',
    eventStatus: status,
    message: stage,
    payload: {
      serviceType,
      stage,
    },
  });
}

async function flushSupabaseSync() {
  if (!hasSupabaseSessionBridge()) {
    return;
  }
  const sessionId = ensureKioskSessionId();
  if (!sessionId) {
    return;
  }
  const stage = sessionState.stage || getCurrentScreen() || null;
  const status = sessionState.status || deriveStatusFromStage(stage);
  const serviceType = sessionState.serviceType || deriveServiceTypeFromStage(stage) || 'thickness';
  const reason = pendingSupabaseReason || 'state';
  pendingSupabaseReason = null;

  await syncKioskSessionState({
    sessionId,
    status,
    serviceType,
    stage,
    state: sessionState,
  });

  if (reason === 'stage-change' && stage) {
    recordStageEvent(sessionId, stage, status, serviceType);
  }
}

function ensureSessionDefaults() {
  if (!sessionState.session || typeof sessionState.session !== 'object') {
    sessionState.session = { kiosk: null, thicknessId: null, obdId: null };
  } else {
    if (!('kiosk' in sessionState.session)) {
      sessionState.session.kiosk = null;
    }
    if (!('thicknessId' in sessionState.session)) {
      sessionState.session.thicknessId = null;
    }
    if (!('obdId' in sessionState.session)) {
      sessionState.session.obdId = null;
    }
  }
  if (typeof sessionState.stage !== 'string') {
    sessionState.stage = null;
  }
  if (typeof sessionState.status !== 'string') {
    sessionState.status = 'created';
  }
  if (!('serviceType' in sessionState)) {
    sessionState.serviceType = null;
  }
  if (!sessionState.serviceType && typeof sessionState.selectedService === 'string') {
    sessionState.serviceType = sessionState.selectedService;
  }
}

function hydrateSessionState() {
  ensureSessionDefaults();
  if (typeof sessionStorage === 'undefined') {
    return;
  }
  try {
    const saved = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      Object.assign(sessionState, parsed);
    }
  } catch (error) {
    console.warn('[session] Failed to hydrate state:', error);
  }
  ensureSessionDefaults();
  if (!sessionState.session.kiosk) {
    sessionState.session.kiosk = generateSessionId('kiosk');
  }
}

/**
 * Deeply assign a nested property inside the in-memory session state.
 * @param {string} key
 * @param {unknown} value
 * @returns {void}
 */
export function setSessionValue(key, value) {
  const keys = key.split('.');
  /** @type {Record<string, unknown>} */
  let target = sessionState;

  for (let i = 0; i < keys.length - 1; i++) {
    const segment = keys[i];
    if (!target[segment] || typeof target[segment] !== 'object') {
      target[segment] = {};
    }
    target = /** @type {Record<string, unknown>} */ (target[segment]);
  }

  const lastKey = keys[keys.length - 1];
  target[lastKey] = value;

  if (key === 'selectedService') {
    sessionState.serviceType = typeof value === 'string' ? /** @type {string} */ (value) : null;
  }

  persistSessionState({ reason: 'state' });
}

/**
 * Reset the mutable state for a fresh customer journey.
 * @returns {void}
 */
export function clearSessionState() {
  sessionState.contact.thickness = null;
  sessionState.contact.diagnostics = null;
  sessionState.session.kiosk = generateSessionId('kiosk');
  sessionState.session.thicknessId = null;
  sessionState.session.obdId = null;
  sessionState.reportSent.thickness = false;
  sessionState.reportSent.diagnostics = false;
  sessionState.selectedService = null;
  sessionState.thicknessType = null;
  sessionState.obdMode = 'general';
  sessionState.obdMake = null;
  sessionState.stage = null;
  sessionState.status = 'created';
  sessionState.serviceType = null;

  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    } catch (error) {
      console.warn('[session] Failed to clear storage:', error);
    }
  }
  persistSessionState({ reason: 'reset' });
}

/**
 * Nudge the watchdog timer that auto-resets the kiosk after inactivity.
 * @returns {void}
 */
export function resetIdleTimer() {
  if (idleTimer) {
    clearTimeout(idleTimer);
  }

  idleTimer = setTimeout(() => {
    console.log('[session] Idle timeout - returning to attract screen');
    clearSessionState();
    showScreen('screen-attract');
  }, config.sessionTimeout);
}

/**
 * Hydrate the session state from storage and register idle listeners.
 * @returns {void}
 */
export function initSessionManager() {
  hydrateSessionState();

  ['click', 'keydown', 'pointerdown', 'touchstart'].forEach(eventName => {
    document.addEventListener(eventName, resetIdleTimer, { passive: true });
  });

  resetIdleTimer();
  initStageTracking();

  persistSessionState({ reason: 'hydrate' });

  console.log('[session] Session manager initialized');
}

/**
 * Generate a collision-resistant session ID for downstream systems.
 * @param {string} prefix
 * @returns {string}
 */
export function generateSessionId(prefix) {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).slice(2, 6);
  return `${prefix}-${timestamp}-${random}`;
}

/**
 * @param {(state: SessionState) => void} listener
 * @returns {() => void}
 */
export function subscribeSessionState(listener) {
  if (typeof listener !== 'function') {
    return () => { };
  }
  sessionListeners.add(listener);
  try {
    listener(sessionState);
  } catch (error) {
    console.error('[session] Listener immediate call failed:', error);
  }
  return () => {
    sessionListeners.delete(listener);
  };
}
