import { apiClient } from './api-client.js';
/**
 * @typedef {Record<string, unknown> & { status?: 'pending' | 'succeeded' | 'failed' | 'canceled' }} PaymentStatus
 */

/**
 * @param {number} amount
 * @param {Record<string, unknown>} [meta]
 * @returns {Promise<PaymentStatus>}
 */
export async function createPaymentIntent(amount, meta = {}) {
  return /** @type {Promise<PaymentStatus>} */ (
    apiClient.post('/payments/intent', {
      amount,
      currency: 'RUB',
      meta,
    })
  );
}

/**
 * @param {string} intentId
 * @returns {Promise<PaymentStatus>}
 */
export async function getPaymentStatus(intentId) {
  return /** @type {Promise<PaymentStatus>} */ (
    apiClient.get(`/payments/${encodeURIComponent(intentId)}/status`)
  );
}

/**
 * @param {string} intentId
 * @returns {Promise<PaymentStatus>}
 */
export async function confirmPaymentDev(intentId) {
  return /** @type {Promise<PaymentStatus>} */ (
    apiClient.post('/payments/confirm-dev', { id: intentId })
  );
}

/**
 * @param {string} intentId
 * @param {(status: PaymentStatus) => void} onUpdate
 * @param {number} [intervalMs]
 * @returns {() => void}
 */
export function startPaymentPolling(intentId, onUpdate, intervalMs = 2000) {
  let stopped = false;

  async function tick() {
    if (stopped) {
      return;
    }

    try {
      const status = await getPaymentStatus(intentId);

      onUpdate(status);

      if (status && (status.status === 'succeeded' ||
        status.status === 'canceled' ||
        status.status === 'failed')) {
        stopped = true;
        return;
      }
    } catch (err) {
      console.warn('[payment] Poll failed:', err);
    }

    if (!stopped) {
      setTimeout(tick, intervalMs);
    }
  }

  tick();

  return () => {
    stopped = true;
  };
}

export function initPaymentClient() {
  console.log('[payment] Payment client initialized');
}
