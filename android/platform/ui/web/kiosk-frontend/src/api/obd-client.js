import { config, getApiUrl, loadConfig } from '../core/config.js';

if (!config.apiBaseUrl) {
  try {
    loadConfig();
  } catch (error) {
    console.warn('[obd-client] Failed to resolve API base', error);
  }
}

const DEFAULT_TIMEOUT = 10000;

/**
 * Perform a fetch request with an abort timeout to avoid hanging network calls.
 * @template T
 * @param {string} url
 * @param {RequestInit} [options]
 * @param {number} [timeout]
 * @returns {Promise<T>}
 */
async function fetchWithTimeout(url, options = {}, timeout = DEFAULT_TIMEOUT) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);
  const endpoint = url.startsWith('http') ? url : getApiUrl(url);

  try {
    const response = await fetch(endpoint, {
      ...options,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);

    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: 'unknown_error' }));
      throw new Error(error.error || error.message || `HTTP ${response.status}`);
    }

    return await response.json();
  } catch (error) {
    clearTimeout(timeoutId);
    if (error instanceof Error && error.name === 'AbortError') {
      throw new Error('Request timeout');
    }
    throw error;
  }
}

/**
 * @returns {Promise<unknown>}
 */
export async function getObdStatus() {
  return await fetchWithTimeout('/api/obd/status');
}

/**
 * @param {string} vehicleMake
 * @param {string} model
 * @param {string} mode
 * @returns {Promise<unknown>}
 */
export async function connectObd(vehicleMake, model, mode) {
  return await fetchWithTimeout('/api/obd/connect', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      vehicleMake,
      model,
      mode,
    }),
  });
}

/**
 * @returns {Promise<unknown>}
 */
export async function disconnectObd() {
  return await fetchWithTimeout('/api/obd/disconnect', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

/**
 * @returns {Promise<unknown>}
 */
export async function getDtcCodes() {
  return await fetchWithTimeout('/api/obd/dtc');
}

/**
 * @param {string} confirmation
 * @returns {Promise<unknown>}
 */
export async function clearDtcCodes(confirmation) {
  return await fetchWithTimeout('/api/obd/dtc/clear', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      confirmation,
    }),
  });
}

/**
 * @returns {Promise<unknown>}
 */
export async function getLivePids() {
  return await fetchWithTimeout('/api/obd/pids/live');
}
