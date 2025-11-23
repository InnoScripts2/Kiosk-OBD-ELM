(function (global) {
    'use strict';

    if (global.__kioskDiagnosticsClientInjected) {
        return;
    }

    const EVENT_NAME = 'kiosk-diagnostics';
    const DEFAULT_TIMEOUT_MS = 120_000;

    const pendingRequests = new Map();
    let counter = 0;

    function ensureNative() {
        if (!global.KioskDiagnostics || typeof global.KioskDiagnostics.postMessage !== 'function') {
            throw new Error('Native diagnostics bridge is not available');
        }
    }

    function nextRequestId() {
        counter += 1;
        return `diag-${Date.now()}-${counter}`;
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
            const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
            const envelope = JSON.stringify({ requestId, action, payload });

            const timeoutHandle = setTimeout(() => {
                pendingRequests.delete(requestId);
                reject(new Error(`Diagnostics request ${action} timed out`));
            }, timeoutMs);

            pendingRequests.set(requestId, {
                resolve,
                reject,
                timeoutHandle,
            });

            try {
                global.KioskDiagnostics.postMessage(envelope);
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
            const err = new Error(error?.message || `Diagnostics action ${action} failed`);
            err.code = error?.code;
            pending.reject(err);
        }
    }

    const api = {
        runReport(payload = {}, options = {}) {
            return send('run_report', payload, options);
        },
        getLastReport(options = {}) {
            return send('get_last_report', {}, options);
        },
        cancelReport(options = {}) {
            return send('cancel_report', {}, options);
        },
        previewSnapshot(payload = {}, options = {}) {
            return send('preview_snapshot', payload, options);
        },
        lookupDictionary(payload = {}, options = {}) {
            return send('dictionary_lookup', payload, options);
        },
        suggestDictionary(payload = {}, options = {}) {
            return send('dictionary_suggest', payload, options);
        },
    };

    global.KioskDiagnosticsClient = api;
    global.__kioskDiagnosticsClientInjected = true;

    global.addEventListener(EVENT_NAME, handleNativeResponse, false);

    console.info('[kiosk-diagnostics] client injected');
})(window);
