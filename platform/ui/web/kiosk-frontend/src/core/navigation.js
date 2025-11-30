let currentScreen = 'screen-attract';
/** @type {string[]} */
const screenHistory = [];
/** @type {Array<(screenId: string) => void>} */
const screenChangeListeners = [];

/**
 * Activate the requested screen panel by ID and notify listeners.
 * @param {string} screenId
 * @returns {void}
 */
export function showScreen(screenId) {
  const screens = document.querySelectorAll('.screen');
  screens.forEach(screen => {
    screen.classList.remove('active');
  });

  const targetScreen = document.getElementById(screenId);
  if (targetScreen) {
    targetScreen.classList.add('active');
    currentScreen = screenId;

    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem('currentScreen', screenId);
    }

    screenChangeListeners.forEach(listener => {
      try {
        listener(screenId);
      } catch (e) {
        console.error('[navigation] Listener error:', e);
      }
    });
  } else {
    console.warn('[navigation] Screen not found:', screenId);
  }
}

/**
 * Remove the active state from a screen if it is currently visible.
 * @param {string} screenId
 * @returns {void}
 */
export function hideScreen(screenId) {
  const screen = document.getElementById(screenId);
  if (screen) {
    screen.classList.remove('active');
  }
}

/**
 * @returns {string}
 */
export function getCurrentScreen() {
  return currentScreen;
}

/**
 * Register a callback to observe screen transitions.
 * @param {(screenId: string) => void} listener
 * @returns {void}
 */
export function onScreenChange(listener) {
  if (typeof listener === 'function') {
    screenChangeListeners.push(listener);
  }
}

/**
 * Wire back buttons and restore persisted navigation data.
 * @returns {void}
 */
export function initNavigation() {
  const backButtons = document.querySelectorAll('[data-back]');

  backButtons.forEach(button => {
    button.addEventListener('click', () => {
      handleBackNavigation();
    });
  });

  console.log('[navigation] Navigation initialized');
}

/**
 * Apply heuristic flow-based back navigation rules.
 * @returns {void}
 */
export function handleBackNavigation() {
  const current = getCurrentScreen();

  const flowRoots = ['screen-thk-intro', 'screen-obd-intro'];
  if (flowRoots.includes(current)) {
    showScreen('screen-services');
    return;
  }

  if (current.startsWith('screen-thk')) {
    showScreen('screen-thk-intro');
    return;
  }

  if (current.startsWith('screen-obd')) {
    showScreen('screen-obd-intro');
    return;
  }

  showScreen('screen-services');
}
