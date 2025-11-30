/**
 * @typedef {Object} KioskConfig
 * @property {string | null} apiBaseUrl
 * @property {string | null} wsBaseUrl
 * @property {boolean} devMode
 * @property {number} sessionTimeout
 * @property {number} attractScreenTimeout
 * @property {{ thickness: number; obd: number; payment: number }} pollInterval
 */

/** @type {KioskConfig} */
export const config = {
  apiBaseUrl: null,
  wsBaseUrl: null,
  devMode: false,
  sessionTimeout: 120000,
  attractScreenTimeout: 5000,
  pollInterval: {
    thickness: 3000,
    obd: 3000,
    payment: 2000,
  },
};

/**
 * Load runtime configuration values from URL params and localStorage.
 * @returns {KioskConfig}
 */
export function loadConfig() {
  const urlParams = new URLSearchParams(window.location.search);

  const apiPort = urlParams.get('apiPort') || '7070';
  const agentParam = urlParams.get('agent');

  if (agentParam && typeof localStorage !== 'undefined') {
    try {
      localStorage.setItem('AGENT_API_BASE', agentParam);
    } catch (e) { }
  }

  if (urlParams.has('clearAgent') && typeof localStorage !== 'undefined') {
    try {
      localStorage.removeItem('AGENT_API_BASE');
    } catch (e) { }
  }

  const storedAgent = typeof localStorage !== 'undefined'
    ? localStorage.getItem('AGENT_API_BASE')
    : null;

  const protocol = window.location.protocol === 'https:' ? 'https' : 'http';
  const host = window.location.hostname || 'localhost';

  const resolvedApiBase = (agentParam && agentParam.trim()) ||
    (storedAgent && storedAgent.trim()) ||
    `${protocol}://${host}:${apiPort}`;

  config.apiBaseUrl = resolvedApiBase;
  config.wsBaseUrl = resolvedApiBase.replace(/^http/, 'ws');

  config.devMode = typeof localStorage !== 'undefined' &&
    localStorage.getItem('devMode') === 'true';

  return config;
}

/**
 * Resolve the full REST API URL relative to the configured backend base.
 * @param {string} path
 * @returns {string}
 */
export function getApiUrl(path) {
  const base = config.apiBaseUrl || 'http://localhost:7070';
  return `${base}${path.startsWith('/') ? path : '/' + path}`;
}

/**
 * Resolve the full WebSocket URL relative to the configured backend base.
 * @param {string} path
 * @returns {string}
 */
export function getWsUrl(path) {
  const base = config.wsBaseUrl || 'ws://localhost:7070';
  return `${base}${path.startsWith('/') ? path : '/' + path}`;
}
