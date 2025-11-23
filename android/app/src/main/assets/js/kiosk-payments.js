(function (global) {
    'use strict';

    if (global.__kioskPaymentsClientInjected) {
        return;
    }

    const EVENT_NAME = 'kiosk-payment';
    const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
    const DEFAULT_POLL_INTERVAL_MS = 2_000;
    const DEFAULT_POLL_TIMEOUT_MS = 10 * 60 * 1_000; // 10 минут

    const pendingRequests = new Map();
    let counter = 0;

    function ensureNative() {
        if (!global.KioskPayments || typeof global.KioskPayments.postMessage !== 'function') {
            throw new Error('Native payments bridge is not available');
        }
    }

    function nextRequestId() {
        counter += 1;
        return `js-${Date.now()}-${counter}`;
    }

    function send(action, payload = {}, options = {}) {
        return new Promise((resolve, reject) => {
            try {
                ensureNative();
            } catch (error) {
                reject(error);
                return;
            }

            const requestId = options.requestId || nextRequestId();
            const timeoutMs = options.timeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS;
            const envelope = JSON.stringify({ requestId, action, payload });

            const timeoutHandle = setTimeout(() => {
                pendingRequests.delete(requestId);
                reject(new Error(`Payment request ${action} timed out`));
            }, timeoutMs);

            pendingRequests.set(requestId, {
                resolve,
                reject,
                timeoutHandle,
            });

            try {
                global.KioskPayments.postMessage(envelope);
            } catch (error) {
                clearTimeout(timeoutHandle);
                pendingRequests.delete(requestId);
                reject(error);
            }
        });
    }

    function handleNativeResponse(event) {
        const detail = event.detail || {};
        const { requestId, ok, data, error, action } = detail;

        if (!requestId || !pendingRequests.has(requestId)) {
            return;
        }

        const pending = pendingRequests.get(requestId);
        pendingRequests.delete(requestId);
        clearTimeout(pending.timeoutHandle);

        if (ok) {
            pending.resolve(data);
        } else {
            const err = new Error(error?.message || `Payment action ${action} failed`);
            err.code = error?.code;
            pending.reject(err);
        }
    }

    function normalizeStatus(status) {
        return (status || '').toString().toLowerCase();
    }

    function isTerminalStatus(status) {
        const normalized = normalizeStatus(status);
        return normalized === 'confirmed' ||
            normalized === 'manual' ||
            normalized === 'failed' ||
            normalized === 'expired';
    }

    function startStatusPolling(intentId, config = {}, onUpdate) {
        let stopped = false;
        const intervalMs = config.intervalMs ?? DEFAULT_POLL_INTERVAL_MS;
        const timeoutMs = config.timeoutMs ?? DEFAULT_POLL_TIMEOUT_MS;
        const deadline = Date.now() + timeoutMs;
        let timerId = null;

        const tick = async () => {
            if (stopped) {
                return;
            }

            try {
                const snapshot = await send('get_status', { id: intentId });
                if (typeof onUpdate === 'function') {
                    try {
                        onUpdate(snapshot);
                    } catch (consumerError) {
                        console.error('[kiosk-payments] polling consumer error', consumerError);
                    }
                }
                if (isTerminalStatus(snapshot?.status)) {
                    stopped = true;
                    return;
                }
            } catch (error) {
                console.warn('[kiosk-payments] polling error', error);
            }

            if (Date.now() >= deadline) {
                stopped = true;
                return;
            }

            timerId = global.setTimeout(tick, intervalMs);
        };

        timerId = global.setTimeout(tick, intervalMs);

        return () => {
            stopped = true;
            if (timerId) {
                global.clearTimeout(timerId);
            }
        };
    }

    const api = {
        createIntent(amount, options = {}) {
            if (typeof amount !== 'number' || !Number.isFinite(amount) || amount <= 0) {
                return Promise.reject(new Error('Amount must be a positive number'));
            }
            const payload = {
                amount,
                currency: options.currency || 'RUB',
                sessionId: options.sessionId || options.session || null,
                serviceType: options.serviceType || options.service || null,
                contact: options.contact || null,
                meta: options.meta || null,
                idempotencyKey: options.idempotencyKey || null,
                expiresInMs: options.expiresInMs || null,
            };
            return send('create_intent', payload, options);
        },
        getIntent(id) {
            return send('get_intent', { id });
        },
        getStatus(id) {
            return send('get_status', { id });
        },
        confirmDev(id) {
            return send('confirm_dev', { id });
        },
        manualConfirm(options = {}) {
            return send('manual_confirm', options);
        },
        moduleInfo() {
            return send('module_info', {});
        },
        pollStatus(intentId, config, onUpdate) {
            return startStatusPolling(intentId, config, onUpdate);
        },
    };

    global.KioskPaymentsClient = api;
    global.__kioskPaymentsClientInjected = true;

    global.addEventListener(EVENT_NAME, handleNativeResponse, false);

    console.info('[kiosk-payments] client injected');
})(window);
