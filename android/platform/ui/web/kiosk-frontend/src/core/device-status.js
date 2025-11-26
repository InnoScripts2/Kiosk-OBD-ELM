import { getWsUrl } from './config.js';

/**
 * @typedef {Object} DeviceStatusPayload
 * @property {string=} status
 * @property {number=} progress
 * @property {string=} message
 */

/**
 * @typedef {Object} DeviceStatusMessage
 * @property {string} type
 * @property {DeviceStatusPayload=} payload
 */

class DeviceStatusManager {
  constructor() {
    /** @type {WebSocket | null} */
    this.ws = null;
    this.reconnectInterval = 5000;
    /** @type {ReturnType<typeof setTimeout> | null} */
    this.reconnectTimer = null;
    /** @type {Map<string, (payload: DeviceStatusPayload) => void>} */
    this.listeners = new Map();
    this.isConnected = false;
  }

  connect() {
    if (this.ws) {
      return;
    }

    try {
      const wsUrl = getWsUrl('/ws/obd');
      this.ws = new WebSocket(wsUrl);

      this.ws.addEventListener('open', () => {
        console.log('[device-status] WebSocket connected');
        this.isConnected = true;
        if (this.reconnectTimer) {
          clearTimeout(this.reconnectTimer);
          this.reconnectTimer = null;
        }
      });

      this.ws.addEventListener('message', (event) => {
        try {
          /** @type {DeviceStatusMessage} */
          const message = JSON.parse(/** @type {string} */(event.data));
          this.handleMessage(message);
        } catch (e) {
          console.error('[device-status] Failed to parse message:', e);
        }
      });

      this.ws.addEventListener('close', () => {
        console.log('[device-status] WebSocket closed');
        this.isConnected = false;
        this.ws = null;
        this.scheduleReconnect();
      });

      this.ws.addEventListener('error', (error) => {
        console.error('[device-status] WebSocket error:', error);
      });
    } catch (e) {
      console.error('[device-status] Failed to connect:', e);
      this.scheduleReconnect();
    }
  }

  scheduleReconnect() {
    if (this.reconnectTimer) {
      return;
    }

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, this.reconnectInterval);
  }

  /**
   * @param {DeviceStatusMessage} message
   * @returns {void}
   */
  handleMessage(message) {
    if (message.type === 'status-update' && message.payload) {
      const payload = message.payload;
      this.updateDeviceUI(payload);

      this.listeners.forEach(listener => {
        try {
          listener(payload);
        } catch (e) {
          console.error('[device-status] Listener error:', e);
        }
      });
    }
  }

  /**
   * @param {DeviceStatusPayload} payload
   * @returns {void}
   */
  updateDeviceUI(payload) {
    const elements = document.querySelectorAll('[data-device="obd"]');

    elements.forEach((el) => {
      if (!(el instanceof HTMLElement)) {
        return;
      }

      if (payload.status) {
        el.setAttribute('data-status', payload.status);
      }
      if (typeof payload.progress === 'number') {
        el.setAttribute('data-progress', String(payload.progress));
      }

      const statusText = el.querySelector('.status-text');
      if (statusText instanceof HTMLElement && payload.message) {
        statusText.textContent = payload.message;
      }
    });
  }

  /**
   * @param {(payload: DeviceStatusPayload) => void} callback
   * @returns {() => void}
   */
  subscribe(callback) {
    const id = Math.random().toString(36).slice(2);
    this.listeners.set(id, callback);
    return () => this.listeners.delete(id);
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.isConnected = false;
  }
}

export const deviceStatus = new DeviceStatusManager();

export function initDeviceStatus() {
  deviceStatus.connect();
  console.log('[device-status] Device status initialized');
}
