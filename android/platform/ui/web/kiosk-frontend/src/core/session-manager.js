import { config } from './config.js';
import { showScreen } from './navigation.js';

/** @typedef {import('../types/global.d.ts').SessionState} SessionState */

/** @type {SessionState & Record<string, unknown>} */
const sessionState = {
  contact: {
    thickness: null,
    diagnostics: null,
  },
  session: {
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
};

/** @type {ReturnType<typeof setTimeout> | null} */
let idleTimer = null;

/**
 * @returns {SessionState}
 */
export function getSessionState() {
  return sessionState;
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

  target[keys[keys.length - 1]] = value;

  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.setItem('sessionState', JSON.stringify(sessionState));
    } catch (e) { }
  }
}

/**
 * Reset the mutable state for a fresh customer journey.
 * @returns {void}
 */
export function clearSessionState() {
  sessionState.contact.thickness = null;
  sessionState.contact.diagnostics = null;
  sessionState.session.thicknessId = null;
  sessionState.session.obdId = null;
  sessionState.reportSent.thickness = false;
  sessionState.reportSent.diagnostics = false;
  sessionState.selectedService = null;
  sessionState.thicknessType = null;
  sessionState.obdMode = 'general';
  sessionState.obdMake = null;

  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.removeItem('sessionState');
    } catch (e) { }
  }
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
  if (typeof sessionStorage !== 'undefined') {
    try {
      const saved = sessionStorage.getItem('sessionState');
      if (saved) {
        const parsed = JSON.parse(saved);
        Object.assign(sessionState, parsed);
      }
    } catch (e) { }
  }

  ['click', 'keydown', 'pointerdown', 'touchstart'].forEach(eventName => {
    document.addEventListener(eventName, resetIdleTimer, { passive: true });
  });

  resetIdleTimer();

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
