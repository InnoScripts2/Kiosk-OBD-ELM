'use strict';

(function initializeRuntimeConfig(global) {
    const SETTINGS_KEY = 'kiosk-settings';
    const EVENT_NAME = 'supabase:config-change';
    const PAYMENT_EVENT = 'supabase:payment-change';
    const runtime = {
        SETTINGS_KEY,
        EVENT_NAME,
        PAYMENT_EVENT,
        loadSettings,
        saveSettings,
        applySupabase,
        clearSupabase,
    };

    let inMemorySettings = null;

    /**
     * @returns {Record<string, unknown>}
     */
    function loadSettings() {
        const stored = readFromLocalStorage();
        if (stored) {
            inMemorySettings = stored;
            return structuredCloneSafe(stored);
        }

        inMemorySettings = { source: 'agent' };
        return structuredCloneSafe(inMemorySettings);
    }

    /**
     * @param {Record<string, unknown>} value
     * @returns {void}
     */
    function saveSettings(value) {
        const normalized = normalizeSettings(value);
        inMemorySettings = normalized;
        writeToLocalStorage(normalized);
    }

    /**
     * @param {Record<string, unknown> | null | undefined} detail
     * @param {{ emitEvent?: boolean }} [options]
     * @returns {void}
     */
    function applySupabase(detail, options) {
        const emitEvent = options?.emitEvent ?? true;
        const config = sanitizeSupabaseDetail(detail);

        if (config) {
            global.__supabaseConfig = config;
        } else {
            delete global.__supabaseConfig;
        }

        if (emitEvent) {
            global.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: config }));
        }
    }

    /**
     * @param {{ emitEvent?: boolean }} [options]
     * @returns {void}
     */
    function clearSupabase(options) {
        applySupabase(null, options);
    }

    /**
     * @returns {Record<string, unknown> | null}
     */
    function readFromLocalStorage() {
        try {
            const raw = global.localStorage?.getItem(SETTINGS_KEY);
            if (!raw) {
                return null;
            }

            const parsed = JSON.parse(raw);
            if (parsed && typeof parsed === 'object') {
                return parsed;
            }
        } catch (error) {
            console.warn('[runtime-config] Не удалось прочитать настройки:', error);
        }

        return null;
    }

    /**
     * @param {Record<string, unknown>} value
     * @returns {void}
     */
    function writeToLocalStorage(value) {
        try {
            global.localStorage?.setItem(SETTINGS_KEY, JSON.stringify(value));
        } catch (error) {
            console.warn('[runtime-config] Не удалось сохранить настройки:', error);
        }
    }

    /**
     * @param {Record<string, unknown> | null | undefined} value
     * @returns {Record<string, unknown>}
     */
    function normalizeSettings(value) {
        if (!value || typeof value !== 'object') {
            return { source: 'agent' };
        }

        const source = typeof value.source === 'string' ? value.source : 'agent';
        const result = { ...value, source };
        return result;
    }

    /**
     * @param {Record<string, unknown> | null | undefined} value
     * @returns {{ url: string; anonKey: string; source: string } | null}
     */
    function sanitizeSupabaseDetail(value) {
        if (!value || typeof value !== 'object') {
            return null;
        }

        const url = typeof value.url === 'string' ? value.url.trim() : '';
        const anonKey = typeof value.anonKey === 'string' ? value.anonKey.trim() : '';
        if (!url || !anonKey) {
            return null;
        }

        return {
            url,
            anonKey,
            source: typeof value.source === 'string' ? value.source : 'runtime',
        };
    }

    /**
     * @template T
     * @param {T} value
     * @returns {T}
     */
    function structuredCloneSafe(value) {
        try {
            if (typeof structuredClone === 'function') {
                return structuredClone(value);
            }
        } catch (error) {
            console.warn('[runtime-config] structuredClone недоступен:', error);
        }

        return JSON.parse(JSON.stringify(value));
    }

    function bootstrapFromStoredSettings() {
        const currentSettings = loadSettings();
        const shouldHydrate =
            currentSettings?.source === 'supabase' &&
            typeof currentSettings.supabaseUrl === 'string' &&
            typeof currentSettings.supabaseAnonKey === 'string';

        if (shouldHydrate) {
            applySupabase(
                {
                    url: currentSettings.supabaseUrl,
                    anonKey: currentSettings.supabaseAnonKey,
                    source: 'storage',
                },
                { emitEvent: false },
            );
        }
    }

    bootstrapFromStoredSettings();
    global.__kioskRuntimeConfig = runtime;
})(window);
