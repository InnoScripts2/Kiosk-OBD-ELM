import { getApiUrl } from './config.js';

class ApiClient {
  constructor() {
    /** @type {number} */
    this.retryCount = 3;
    /** @type {number} */
    this.retryDelay = 1000;
  }

  /**
   * Execute a JSON API request with exponential backoff.
   * @param {string} url
   * @param {RequestInit} [options]
   * @returns {Promise<unknown>}
   */
  async request(url, options = {}) {
    const fullUrl = url.startsWith('http') ? url : getApiUrl(url);

    /** @type {unknown} */
    let lastError = null;
    for (let attempt = 0; attempt < this.retryCount; attempt++) {
      try {
        const headers = new Headers(options.headers || undefined);
        headers.set('Content-Type', 'application/json');

        const response = await fetch(fullUrl, {
          ...options,
          headers,
        });

        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
          throw Object.assign(
            new Error(data?.message || data?.error || 'request_failed'),
            { data, status: response.status }
          );
        }

        return data;
      } catch (error) {
        lastError = error;

        if (attempt < this.retryCount - 1) {
          await this.delay(this.retryDelay * Math.pow(2, attempt));
        }
      }
    }

    if (lastError instanceof Error) {
      throw lastError;
    }

    throw new Error('request_failed');
  }

  /**
   * @param {string} url
   * @returns {Promise<unknown>}
   */
  async get(url) {
    return this.request(url, { method: 'GET' });
  }

  /**
   * @param {string} url
   * @param {unknown} [body]
   * @returns {Promise<unknown>}
   */
  async post(url, body = {}) {
    return this.request(url, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  /**
   * @param {string} url
   * @param {unknown} [body]
   * @returns {Promise<unknown>}
   */
  async put(url, body = {}) {
    return this.request(url, {
      method: 'PUT',
      body: JSON.stringify(body),
    });
  }

  /**
    * @param {string} url
    * @returns {Promise<unknown>}
    */
  async delete(url) {
    return this.request(url, { method: 'DELETE' });
  }

  /**
   * @param {number} ms
   * @returns {Promise<void>}
   */
  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

export const apiClient = new ApiClient();
