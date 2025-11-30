import { config } from './config.js';
import { KioskConfig } from './kiosk-config.js';

/**
 * Determine whether the UI should expose developer-only affordances.
 * @returns {boolean}
 */
export function isDevMode() {
  // В PROD никогда не включаем dev-режим
  if (KioskConfig.isProd()) {
    return false;
  }
  return config.devMode || import.meta.env.DEV || KioskConfig.shouldShowDevButton();
}

export function enableDevMode() {
  if (KioskConfig.isProd()) {
    console.warn('[dev-mode] Cannot enable dev mode in PROD');
    return;
  }
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem('devMode', 'true');
    config.devMode = true;
    updateDevUI();
    showDevNotification('Dev mode enabled');
  }
}

export function disableDevMode() {
  if (typeof localStorage !== 'undefined') {
    localStorage.removeItem('devMode');
    config.devMode = false;
    updateDevUI();
    showDevNotification('Dev mode disabled');
  }
}

export function toggleDevMode() {
  if (KioskConfig.isProd()) {
    console.warn('[dev-mode] Dev mode toggle blocked in PROD');
    return;
  }
  if (isDevMode()) {
    disableDevMode();
  } else {
    enableDevMode();
  }
}

/**
 * Show or hide DEV-only markers depending on the current mode.
 * @returns {void}
 */
function updateDevUI() {
  const devElements = document.querySelectorAll('[data-dev-only], .btn-dev, #thk-dev-mark, #skip-button');
  const shouldShow = isDevMode();

  devElements.forEach((el) => {
    if (el instanceof HTMLElement) {
      el.style.display = shouldShow ? '' : 'none';
      if (shouldShow) {
        el.removeAttribute('disabled');
      } else {
        el.setAttribute('disabled', 'true');
      }
    }
  });

  // Dev-примечания (только DEV/QA)
  const devNotes = document.querySelectorAll('.muted-note');
  devNotes.forEach((note) => {
    if (!(note instanceof HTMLElement)) {
      return;
    }
    const text = note.textContent || '';
    if (text.includes('DEV') || text.includes('PROD')) {
      note.style.display = KioskConfig.isProd() ? 'none' : '';
    }
  });

  let indicator = document.getElementById('dev-mode-indicator');
  if (shouldShow) {
    if (!indicator) {
      indicator = document.createElement('div');
      indicator.id = 'dev-mode-indicator';
      indicator.className = 'dev-mode-indicator';
      indicator.textContent = `${KioskConfig.APP_MODE} MODE`;
      indicator.setAttribute('aria-live', 'polite');
      document.body.appendChild(indicator);
    }
  } else {
    if (indicator) {
      indicator.remove();
    }
  }
}

/**
 * Display a short-lived toast in the corner of the kiosk UI.
 * @param {string} message
 * @returns {void}
 */
function showDevNotification(message) {
  const notification = document.createElement('div');
  notification.className = 'dev-notification';
  notification.textContent = message;
  notification.setAttribute('role', 'status');
  notification.setAttribute('aria-live', 'polite');

  document.body.appendChild(notification);

  setTimeout(() => {
    notification.classList.add('dev-notification--show');
  }, 10);

  setTimeout(() => {
    notification.classList.remove('dev-notification--show');
    setTimeout(() => {
      notification.remove();
    }, 300);
  }, 3000);
}

let touchCount = 0;
/** @type {ReturnType<typeof setTimeout> | null} */
let touchTimer = null;
const TOUCH_THRESHOLD = 3;
const TOUCH_DURATION = 5000;

export function initDevMode() {
  updateDevUI();

  // Блокируем Ctrl+Shift+D в PROD
  document.addEventListener('keydown', (e) => {
    if (e.ctrlKey && e.shiftKey && e.key === 'D') {
      e.preventDefault();
      if (!KioskConfig.isProd()) {
        toggleDevMode();
      } else {
        console.warn('[dev-mode] Toggle shortcut disabled in PROD');
      }
    }
  });

  // Блокируем 3-finger жест в PROD
  document.addEventListener('touchstart', (e) => {
    if (KioskConfig.isProd()) {
      return;
    }

    if (e.touches.length >= TOUCH_THRESHOLD) {
      if (!touchTimer) {
        touchCount = 0;
        touchTimer = setTimeout(() => {
          touchTimer = null;
          touchCount = 0;
        }, TOUCH_DURATION);
      }

      touchCount++;

      if (touchCount >= 3) {
        enableDevMode();
        touchCount = 0;
        if (touchTimer) {
          clearTimeout(touchTimer);
          touchTimer = null;
        }
      }
    }
  });

  console.log(`[dev-mode] Dev mode initialized: APP_MODE=${KioskConfig.APP_MODE}, isDevMode=${isDevMode()}`);
}

/**
 * Утилита для условного логирования в DEV/QA
 */
/**
 * @param {...unknown} args
 */
export function devLog(...args) {
  if (!KioskConfig.isProd()) {
    console.log('[dev]', ...args);
  }
}

/**
 * Утилита для условного предупреждения в DEV/QA
 */
/**
 * @param {...unknown} args
 */
export function devWarn(...args) {
  if (!KioskConfig.isProd()) {
    console.warn('[dev]', ...args);
  }
}

